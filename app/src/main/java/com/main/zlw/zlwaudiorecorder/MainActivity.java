package com.main.zlw.zlwaudiorecorder;

import android.Manifest;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.main.zlw.zlwaudiorecorder.base.MyApp;
import com.zlw.loggerlib.Logger;
import com.zlw.main.recorderlib.RecordManager;
import com.zlw.main.recorderlib.recorder.RecordConfig;
import com.zlw.main.recorderlib.recorder.RecordHelper;
import com.zlw.main.recorderlib.recorder.listener.RecordFftDataListener;
import com.zlw.main.recorderlib.recorder.listener.RecordResultListener;
import com.zlw.main.recorderlib.recorder.listener.RecordSoundSizeListener;
import com.zlw.main.recorderlib.recorder.listener.RecordStateListener;
import com.zlw.main.recorderlib.recorder.mp3.Mp3EncodeThread;
import com.zlw.main.recorderlib.recorder.wav.WavUtils;
import com.zlw.main.recorderlib.utils.ByteUtils;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    @BindView(R.id.btRecord)
    Button btRecord;
    @BindView(R.id.btStop)
    Button btStop;
    @BindView(R.id.tvState)
    TextView tvState;
    @BindView(R.id.tvSoundSize)
    TextView tvSoundSize;
    @BindView(R.id.rgAudioFormat)
    RadioGroup rgAudioFormat;
    @BindView(R.id.rgSimpleRate)
    RadioGroup rgSimpleRate;
    @BindView(R.id.tbEncoding)
    RadioGroup tbEncoding;
    @BindView(R.id.audioView)
    AudioView audioView;
    @BindView(R.id.spUpStyle)
    Spinner spUpStyle;
    @BindView(R.id.spDownStyle)
    Spinner spDownStyle;
    @BindView(R.id.recFilePath)
    TextView recFilePath;

    private boolean isStart = false;
    private boolean isPause = false;
    final RecordManager recordManager = RecordManager.getInstance();
    private static final String[] STYLE_DATA = new String[]{"STYLE_ALL", "STYLE_NOTHING", "STYLE_WAVE", "STYLE_HOLLOW_LUMP"};
    private static final String[] PERMISSIONS = new String[]{
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        initAudioView();
        initEvent();
        initRecord();

        ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS, 1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        doStop();
        initRecordEvent();
    }

    @Override
    protected void onStop() {
        super.onStop();
        doStop();
    }

    private void initAudioView() {
        tvState.setVisibility(View.GONE);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, STYLE_DATA);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spUpStyle.setAdapter(adapter);
        spDownStyle.setAdapter(adapter);
        spUpStyle.setOnItemSelectedListener(this);
        spDownStyle.setOnItemSelectedListener(this);

        audioViewInner = findViewById(R.id.audioViewInner);
        audioViewInner.setStyle(AudioView.ShowStyle.STYLE_HOLLOW_LUMP, AudioView.ShowStyle.STYLE_NOTHING);
    }

    private void initEvent() {
        rgAudioFormat.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.rbPcm:
                        recordManager.changeFormat(RecordConfig.RecordFormat.PCM);
                        break;
                    case R.id.rbMp3:
                        recordManager.changeFormat(RecordConfig.RecordFormat.MP3);
                        break;
                    case R.id.rbWav:
                        recordManager.changeFormat(RecordConfig.RecordFormat.WAV);
                        break;
                    default:
                        break;
                }
            }
        });

        rgSimpleRate.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.rb8K:
                        recordManager.changeRecordConfig(recordManager.getRecordConfig().setSampleRate(8000));
                        break;
                    case R.id.rb16K:
                        recordManager.changeRecordConfig(recordManager.getRecordConfig().setSampleRate(16000));
                        break;
                    case R.id.rb44K:
                        recordManager.changeRecordConfig(recordManager.getRecordConfig().setSampleRate(44100));
                        break;
                    default:
                        break;
                }
            }
        });

        tbEncoding.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.rb8Bit:
                        recordManager.changeRecordConfig(recordManager.getRecordConfig().setEncodingConfig(AudioFormat.ENCODING_PCM_8BIT));
                        break;
                    case R.id.rb16Bit:
                        recordManager.changeRecordConfig(recordManager.getRecordConfig().setEncodingConfig(AudioFormat.ENCODING_PCM_16BIT));
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void initRecord() {
        recordManager.init(MyApp.getInstance(), BuildConfig.DEBUG);
        recordManager.changeFormat(RecordConfig.RecordFormat.WAV);
        String recordDir = String.format(Locale.getDefault(), "%s/Record/com.zlw.main/",
                Environment.getExternalStorageDirectory().getAbsolutePath());
        recordManager.changeRecordDir(recordDir);
        initRecordEvent();
    }

    private void initRecordEvent() {
        recordManager.setRecordStateListener(new RecordStateListener() {
            @Override
            public void onStateChange(RecordHelper.RecordState state) {
                Logger.i(TAG, "onStateChange %s", state.name());

                switch (state) {
                    case PAUSE:
                        tvState.setText("暂停中");
                        break;
                    case IDLE:
                        tvState.setText("空闲中");
                        break;
                    case RECORDING:
                        tvState.setText("录音中");
                        break;
                    case STOP:
                        tvState.setText("停止");
                        break;
                    case FINISH:
                        tvState.setText("录音结束");
                        tvSoundSize.setText("---");
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onError(String error) {
                Logger.i(TAG, "onError %s", error);
            }
        });
        recordManager.setRecordSoundSizeListener(new RecordSoundSizeListener() {
            @Override
            public void onSoundSize(int soundSize) {
                tvSoundSize.setText(String.format(Locale.getDefault(), "声音大小：%s db", soundSize));
            }
        });
        recordManager.setRecordResultListener(new RecordResultListener() {
            @Override
            public void onResult(File result) {
                recFile = result.getAbsolutePath();
                recFilePath.setText(recFile);
                Toast.makeText(MainActivity.this, "录音文件： " + result.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            }
        });
        recordManager.setRecordFftDataListener(new RecordFftDataListener() {
            @Override
            public void onFftData(byte[] data) {
                audioView.setWaveData(data);
            }
        });
    }

    private Visualizer visualizer;
    private AudioVisualConverter audioVisualConverter;
    private boolean isInit = false;
    private AudioView audioViewInner;
    File genFile = null;
    File genMp3File = null;
    FileOutputStream dos = null;
    private Visualizer.OnDataCaptureListener dataCaptureListener = new Visualizer.OnDataCaptureListener() {
        @Override
        public void onWaveFormDataCapture(Visualizer visualizer, final byte[] waveform, int samplingRate) {
            audioViewInner.post(new Runnable() {
                @Override
                public void run() {
                    audioViewInner.setWaveData(waveform);
                    if (dos != null) {
                        try {
                            dos.write(waveform);
                            dos.flush();
                        } catch (Exception e) {
                            e.printStackTrace();
                            Logger.e(TAG, "音频写入文件失败");
                        }
                    }
                }
            });
        }

        @Override
        public void onFftDataCapture(Visualizer visualizer, final byte[] fft, int samplingRate) {
            audioViewInner.post(new Runnable() {
                @Override
                public void run() {
                    //audioView2.setWaveData(fft);
                    tvSoundSize.setText(String.format(Locale.getDefault(), "当前分贝: %s db", audioVisualConverter.getVoiceSize(fft)));
                }
            });
        }
    };

    private void initVisualizer() {
        if (isInit) {
            return;
        }
        isInit = true;

        audioVisualConverter = new AudioVisualConverter();
        Logger.d(TAG, "initVisualizer()");
        try {

            int mediaPlayerId = 0;//MyMediaPlayer.getInstance().getMediaPlayerId();
            Logger.i(TAG, "mediaPlayerId: %s", mediaPlayerId);
            if (visualizer != null) {
                visualizer.release();
            }
            visualizer = new Visualizer(mediaPlayerId);

            int captureSize = Visualizer.getCaptureSizeRange()[1];
            int captureRate = Visualizer.getMaxCaptureRate() * 3 / 4;
            Logger.d(TAG, "精度: %s", captureSize);
            Logger.d(TAG, "刷新频率: %s", captureRate);

            visualizer.setCaptureSize(captureSize);
            visualizer.setDataCaptureListener(dataCaptureListener, captureRate, true, true);
            visualizer.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);
            visualizer.setEnabled(true);
        } catch (Exception e) {
            e.printStackTrace();
            Logger.e(TAG, "请检查录音权限, error=" + e.getMessage());
            isInit = false;
        }
    }

    private Mp3EncodeThread mp3EncodeThread;
    private static final int RECORD_AUDIO_BUFFER_TIMES = 1;

    private void doInterRecStop() {
        if (dos != null) {
            try {
                dos.close();
                dos = null;

                //makeWavFile();
                recFilePath.setText(genFile.getAbsolutePath());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void doGenMp3() {
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelCount, AudioFormat.ENCODING_PCM_8BIT) * RECORD_AUDIO_BUFFER_TIMES;
        mp3EncodeThread = new Mp3EncodeThread(genMp3File, bufferSize);
        mp3EncodeThread.start();

        if (mp3EncodeThread != null) {
            try {
                FileInputStream is = new FileInputStream(genFile);
                BufferedInputStream bis = new BufferedInputStream(is);
                DataInputStream dis = new DataInputStream(bis);
                byte[] waveform = new byte[1024];
                while (dis.available() > 0) {
                    dis.read(waveform, 0, 1024);
                    short[] byteBuffer = ByteUtils.toShorts(waveform);
                    mp3EncodeThread.addChangeBuffer(new Mp3EncodeThread.ChangeBuffer(byteBuffer, byteBuffer.length));
                }
                if (mp3EncodeThread != null) {
                    mp3EncodeThread.stopSafe(new Mp3EncodeThread.EncordFinishListener() {
                        @Override
                        public void onFinish() {
                            mp3EncodeThread = null;
                            recFilePath.setText(genMp3File.getAbsolutePath());
                        }
                    });
                } else {
                    Logger.e(TAG, "mp3EncodeThread is null, 代码业务流程有误，请检查！！ ");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void doGenWav() {
        makeWavFile();
    }

    int sampleRate = 44100;
    int channelCount = 1;
    int sampleBits = 8;

    private void makeWavFile() {
        //genFile = new File("/storage/emulated/0/MusicVisual1/record_20190901_18_45_02.pcm");
        byte[] header = WavUtils.generateWavFileHeader((int) genFile.length(), sampleRate, channelCount, sampleBits);
        try {
            WavUtils.writeHeader(genFile, header);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String pcmPath = genFile.getAbsolutePath();
        String wavPath = pcmPath.substring(0, pcmPath.length() - 4) + ".wav";
        File file = new File(wavPath);
        genFile.renameTo(file);
        recFilePath.setText(file.getAbsolutePath());
    }

    private void doInterRec() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        File folder = new File("/storage/emulated/0/MusicVisual1/");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        genFile = new File(folder.getAbsolutePath() + "/" + sdf.format(new Date()) + ".pcm");

        genMp3File = new File(folder.getAbsolutePath() + "/" + sdf.format(new Date()) + ".mp3");
        if (genFile.exists()) {
            genFile.delete();
        }
        if (genMp3File.exists()) {
            genMp3File.delete();
        }
        try {
            genFile.createNewFile();
            genMp3File.createNewFile();
            dos = new FileOutputStream(genFile);

        } catch (IOException e) {
            e.printStackTrace();
        }
        initVisualizer();
    }

    String recFile = "";
    AudioTrack visualizedTrack = null;

    private void doPlayMusic(String recFile) {
        if (recFile == null || recFile == "") {
            return;
        }
        File genFile1 = new File(recFile);
        if (genFile1 == null) {
            return;
        }
        if (recFile.endsWith(".pcm")) {
            //读取文件
            int musicLength = (int) (genFile1.length());
            byte[] music = new byte[musicLength];
            try {
                InputStream is = new FileInputStream(genFile1);
                BufferedInputStream bis = new BufferedInputStream(is);
                DataInputStream dis = new DataInputStream(bis);
                int i = 0;
                while (dis.available() > 0) {
                    music[i] = dis.readByte();
                    i++;
                }
                dis.close();
                final int minBufferSize = AudioTrack.getMinBufferSize(sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_8BIT);
                visualizedTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_8BIT, minBufferSize, AudioTrack.MODE_STREAM);
                visualizedTrack.play();
                visualizedTrack.write(music, 0, musicLength);
                visualizedTrack.stop();
            } catch (Throwable t) {
                t.printStackTrace();
                Logger.e(TAG, "播放失败");
            }
        } else {
            MyMediaPlayer mediaPlayer = MyMediaPlayer.getInstance();
            mediaPlayer.play(Uri.parse(recFile));
            /*mediaPlayer.setPlayStateListener(new MyMediaPlayer.PlayStateListener() {
                @Override
                public void onStateChange(MyMediaPlayer.PlayState state) {
                    if (state == MyMediaPlayer.PlayState.STATE_PLAYING) {
                        initVisualizer();
                    }
                }
            });*/
        }
    }

    @OnClick({R.id.btnStaticGenMp3, R.id.btnStaticGenWav, R.id.btnInterRecStop, R.id.btnInterRec, R.id.btRecord, R.id.btnPlay, R.id.btStop, R.id.jumpTestActivity})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.btnStaticGenMp3:
                doGenMp3();
                break;
            case R.id.btnStaticGenWav:
                doGenWav();
                break;
            case R.id.btRecord:
                doPlay();
                break;
            case R.id.btStop:
                doStop();
                break;
            case R.id.btnInterRecStop:
                doInterRecStop();
                break;
            case R.id.btnInterRec:
                doInterRec();
                break;
            case R.id.btnPlay:
                doPlayMusic(recFilePath.getText().toString());
                break;
            case R.id.jumpTestActivity:
                startActivity(new Intent(this, TestHzActivity.class));
            default:
                break;
        }
    }

    private void doStop() {
        recordManager.stop();
        btRecord.setText("开始");
        isPause = false;
        isStart = false;
    }

    private void doPlay() {
        if (isStart) {
            recordManager.pause();
            btRecord.setText("开始");
            isPause = true;
            isStart = false;
        } else {
            if (isPause) {
                recordManager.resume();
            } else {
                recordManager.start();
            }
            btRecord.setText("暂停");
            isStart = true;
        }
    }


    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        switch (parent.getId()) {
            case R.id.spUpStyle:
                audioView.setStyle(AudioView.ShowStyle.getStyle(STYLE_DATA[position]), audioView.getDownStyle());
                break;
            case R.id.spDownStyle:
                audioView.setStyle(audioView.getUpStyle(), AudioView.ShowStyle.getStyle(STYLE_DATA[position]));
                break;
            default:
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        //nothing
    }
}
