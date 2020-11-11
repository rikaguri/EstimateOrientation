package com.example.estimateorientation;
// 加速度センサ・地磁気センサからスマホの向きを判定する


import androidx.annotation.RequiresApi;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.lang.Object;

import static java.nio.charset.StandardCharsets.UTF_8;


@RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
public class MainActivity extends Activity implements SensorEventListener{

    final static float PI = (float)Math.PI;

    private SensorManager sensorManager;
    Sensor aSensor;  // 加速度センサ TYPE_ACCELEROMETER
    Sensor grSensor; //重力センサ TYPE_GRAVITY
    Sensor gSensor; // ジャイロセンサ TYPE_GYROSCOPE
    Sensor mSensor; //地磁気センサ TYPE_MAGNETIC_FIELD
    Sensor oSensor; //方位センサ TYPE_ORIENTATION
    private TextView azimuth, moveTextx, moveTexty, moveTextz;

    //ラジアンを角度にする
	protected final static double RAD2DEG = 180/Math.PI;

    //移動方向推定
    private float[] accelValue = new float[3];  //加速度
    private float[] geomagnetic = new float[3]; //地磁気
    private float[] gravity = new float[3];    //重力
    private float[] north = new float[3];       //N軸
    private float[] east = new float[3];        //E軸
    //GNE軸への射影成分
    private float[] gneAccel = new float[3];
    private float[] orientation = new float[3]; //方位角（degree）
    private float orientationRad = 0;   //方位角（rad）
    //移動方向推定
    private long lastAccelTime = 0;
    private float[] speed = new float[3];   //速度
    private float[] difference = new float[3];  //変位
    private boolean diffFlag = true;
    //回転行列
    private float[] rotationMatrix = new float[9];
    //閾値
    protected final static double THRESHOLD = 1.5;
    protected final static double THRESHOLD_MIN=1;
    //ローパスフィルタのα値
    protected final static double alpha =0.1;
	//端末が実際に取得した加速度値。重力加速度も含まれる。This values include gravity force.
	private float[] currentOrientationValues = { 0.0f, 0.0f, 0.0f };
	//ローパス、ハイパスフィルタ後の加速度値 Values after low pass and high pass filter
	private float[] currentAccelerationValues = { 0.0f, 0.0f, 0.0f };

	//diff 差分
	private float dx=0.0f;
	private float dy=0.0f;
	private float dz=0.0f;

	//previous data 1つ前の値
	private float old_x=0.0f;
	private float old_y=0.0f;
	private float old_z=0.0f;

	//ベクトル量
	private double vectorSize=0;

	//カウンタ
	long counter=0;

	//一回目のゆれを省くカウントフラグ（一回の端末の揺れで2回データが取れてしまうのを防ぐため）
	//count flag to prevent aquiring data twice with one movement of a device
	boolean counted=true;

	// X軸加速方向
	boolean vecx = true;
	// Y軸加速方向
	boolean vecy = true;
	// Z軸加速方向
	boolean vecz = true;


	//ノイズ対策
	boolean noiseflg=true;
	//ベクトル量(最大値)
	private double vectorSize_max=0;

    private Button button;
    private Button upload;
    boolean check = false;
    private ImageView imageView;
    private Bitmap bitmap;
	private int imageWidth;
    private int imageHeight;
    private double x;
    private double y;
    private double size;

    private ArrayList<MainActivity.MyData> finDataList= new ArrayList<>();
    private String finData="";
    private BufferedWriter bw;

    public class MyData{
        public Long time;
        public String x_accel;
        public String y_accel;
        public String z_accel;
    }
    // 初期化
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // SensorManagerのインスタンス
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // 標準角加速度を登録
        // 引数を変えると違う種類のセンサ値を取得
        aSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        grSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        gSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        // TextViewのインスタンス
        azimuth = findViewById(R.id.text_1);    //方位角
        moveTextx = findViewById(R.id.text_2);  //X
        moveTexty = findViewById(R.id.text_3);  //Y
        moveTextz = findViewById(R.id.text_4);  //Z

        //ボタンの登録・初期化
        button = (Button)findViewById(R.id.button1);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(check){
                    //difference = unit(difference);  //正規化

                    //表示
                    moveTextx.setText("X:" + String.valueOf(difference[0]));
                    moveTexty.setText("Y:" + String.valueOf(difference[1]));
                    moveTextz.setText("Z:" + String.valueOf(difference[2]));

//                    //ついでに出力用に
//                    //現在時刻を取得
//                    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
//                    //int型にする
//                    long time = System.currentTimeMillis();//currentTimeMIllis()はlong型で時刻を出しているので
//                    MyData mydata = new MyData();
//                    mydata.time= time;
//                    mydata.x_accel = String.valueOf(difference[0]);
//                    mydata.y_accel = String.valueOf(difference[1]);
//                    mydata.z_accel = String.valueOf(difference[2]);
//                    finDataList.add(mydata);

                    //リセット
                    lastAccelTime = 0;
                    int i = 0;
                    for (i = 0; i < 3; i++){
                        speed[i] = 0;
                        difference[i] = 0;
                    }

                    check = false;
                    button.setText("▶");
                }

                else{

                    lastAccelTime = 0;

                    //N軸，E軸の設定
                    orientationRad = orientation[0];    //degree[°]をradianへ
                    float oriNorth = (float)Math.PI / 2 + orientation[0];   //π/2+θ
                    float oriEast = orientation[0];                         //θ
                    //N軸
                    north[0] = (float)Math.cos(oriNorth);
                    north[1] = (float)Math.sin(oriNorth);
                    north[2] = -1.0f * (north[0] * gravity[0] + north[1] * gravity[1]) / gravity[2];
                    north = unit(north);    //正規化
                    //E軸
                    east[0] = (float)Math.cos(oriEast);
                    east[1] = (float)Math.sin(oriEast);
                    east[2] = -1.0f * (east[0] * gravity[0] + east[1] * gravity[1]) / gravity[2];
                    east = unit(east);  //正規化

                    check = true;
                    button.setText("■");

                }
            }
        });
        button.setText("▶︎");

        //upload button
        upload =(Button)findViewById(R.id.upload_button);
        upload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //ここでcsvのデータ形式にする
                createCSVData();
                int time = (int) System.currentTimeMillis();//currentTimeMIllis()はlong型で時刻を出しているので
                CSVcreator(time+".csv",finData);
                //CSVcreator("save.csv",finSaveData);
                //初期化
                finData ="";
            }
        });

        imageView = findViewById(R.id.imageView);
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(),R.drawable.arrow);
	    //画像の横と縦のサイズを取得
	    imageWidth = bitmap.getWidth();
	    imageHeight = bitmap.getHeight();
	    imageView.setImageBitmap(bitmap);

    }

    // Activityが表示された時
    @Override
    protected void onResume() {
        super.onResume();
        // registerListener：監視を開始
        // SENSOR_DELAY_UIを変更すると更新頻度が変わる SENSOR_DELAY_FASTEST:0ms,
        // SENSOR_DELAY_GAME:20ms, SENSOR_DELAY_UI:60ms, SENSOR_DELAY_NORMAL:200ms
        sensorManager.registerListener(this, aSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, grSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, gSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, oSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    // 解除するコード
    @Override
    protected void onPause() {
        super.onPause();
        // Listenerを解除
        sensorManager.unregisterListener(this);
    }

    //センサー
    @Override
    public void onSensorChanged(SensorEvent event) {
        int i = 0;

        switch (event.sensor.getType()){

            case Sensor.TYPE_ACCELEROMETER:   //加速度m/s^2

	            // 取得 Acquiring data

	            for(int j=0;j<3;j++) {
	            	// ローパスフィルタで重力値を抽出　Isolate the force of gravity with the low-pass filter.
		            currentOrientationValues[j] = (float) (event.values[j] * alpha + currentOrientationValues[j] * (1.0f - alpha));
		            // 重力の値を省くRemove the gravity contribution with the high-pass filter.
		            currentAccelerationValues[j] = event.values[j] - currentOrientationValues[j];
	            }

	            //int型にする
	            long time = System.currentTimeMillis();//currentTimeMIllis()はlong型で時刻を出しているので
	            MyData mydata = new MyData();
	            mydata.time= time;
	            mydata.x_accel = String.valueOf(currentAccelerationValues[0]);
	            mydata.y_accel = String.valueOf(currentAccelerationValues[1]);
	            mydata.z_accel = String.valueOf(currentAccelerationValues[2]);
	            finDataList.add(mydata);

	            // ベクトル値を求めるために差分を計算　diff for vector
	            dx = currentAccelerationValues[0] - old_x;
	            dy = currentAccelerationValues[1] - old_y;
	            dz = currentAccelerationValues[2] - old_z;

	            vectorSize = Math.sqrt((double) (dx * dx + dy * dy + dz * dz));

	            // 一回目はノイズになるから省く
	            if (noiseflg) {
		            noiseflg = false;
	            } else {

		            if (vectorSize > THRESHOLD /* && dz <0.0f */) {
			            if (counted == true) {
				            counter++;
				            counted = false;
				            // System.out.println("count is "+counter);
				            //出力
				            size = Math.sqrt(dx*dx+dy*dy);
				            x=dx/size;
				            y=dy/size;

				            imageView.setRotation((float) (Math.atan2(y,x)*180.0/Math.PI));
				            // 最大値なら格納
				            if (vectorSize > vectorSize_max) {
					            vectorSize_max = vectorSize;
				            }
			            } else if(counted== false) {
				            counted = true;

			            }

		            }
	            }

	            // 状態更新
	            //vectorSize_old = vectorSize;
	            old_x = currentAccelerationValues[0];
	            old_y = currentAccelerationValues[1];
	            old_z = currentAccelerationValues[2];
                break;


            case Sensor.TYPE_GRAVITY:   //重力を取得
                gravity = event.values.clone();
                gravity = unit(gravity);    //正規化
                break;

            case  Sensor.TYPE_MAGNETIC_FIELD:
                geomagnetic = event.values.clone();
                if(gravity != null && geomagnetic != null){
                    float R[] = new float[9];
                    float I[] = new float[9];
                    boolean success = SensorManager.getRotationMatrix(R,I,gravity,geomagnetic);
                    if(success){
                        SensorManager.getOrientation(R, orientation);
                        azimuth.setText("方位:" + String.valueOf(orientation));
                    }
                }
                break;


            default:
                return;
        }
    }


    private float[] unit(float[] vec){
        //入力されたベクトルを正規化
        float[] unitVec = new float[vec.length];
        float scalar = (float)Math.sqrt(Math.pow(vec[0],2) + Math.pow(vec[1],2) + Math.pow(vec[2],2));
        for(int i = 0; i < 3; i++){
            unitVec[i] = vec[i] / scalar;
        }
        return  unitVec;
    }

    private float[] rad2deg(float[] vec){
        //入力された値をradianからdegreeに変換
        int VEC_SIZE = vec.length;
        float[] retvec = new float[VEC_SIZE];
        for(int i = 0; i < VEC_SIZE; i++){
            retvec[i] = vec[i] / (float)Math.PI*180;
        }
        return retvec;
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void createCSVData(){//CSVのデータにするときに使う関数、ここで同スキャン別スキャン判定等々を行う

        int index;
        //以下取ってきたものを全てcsv形式に直すもの
        for(index=0;index<finDataList.size();index++){
                finData= finData+ "\n"+ String.valueOf(finDataList.get(index).time)+","+finDataList.get(index).x_accel +","+finDataList.get(index).y_accel +","+finDataList.get(index).z_accel;
        }
    }

    private void CSVcreator(String output, String data){
        //ファイルの読み書きの話
        File path = getExternalFilesDir(null);
        File file = new File(path, output);
        if(file.exists()){
            file.delete();
            //ShowToast("Delete");
        }
        try {
            FileOutputStream outputStream = new FileOutputStream(file, true);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, UTF_8);
            //outputStream.write(finData.getBytes());
            //outputStream.close();
            bw = new BufferedWriter(outputStreamWriter);
            data = data+"\n";
            bw.write(data);
            bw.flush();
            bw.close();
            //ShowToast("STOP"+ data);
        }catch(Exception e){
            e.printStackTrace();
            //ShowToast("NG"+ data);
        }
    }

}