package net.nixonnet.musicvol;

import android.app.Activity;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Visualizer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.CompoundButton;
import android.util.Log;

public class MainActivity extends Activity {

	private SeekBar volSlider;
	private NumberPicker sessionPicker;
	private Equalizer eq;
	private short volumeLimits[] = {-500,500};
	private short initialValues[];
	private TextView volDisp;
	private Switch masterSwitch;
	private Button scanButton;
	private Button guessButton;
	private Handler scanHandler;
	private Runnable sessionIncrementer;
	private boolean foundSession;//via listening with the Visualizer
	private boolean scanning;//with the scan button
	private Handler guessHandler;
	private MediaPlayer silentPlayer;//to prevent bypass
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		volSlider = (SeekBar) findViewById(R.id.seekBar1);
		volSlider.setMax(100);
		volSlider.setProgress(25);
		volSlider.setOnSeekBarChangeListener(new VolumeAdjuster());
		
		sessionPicker = (NumberPicker) findViewById(R.id.numberPicker1);
		sessionPicker.setValue(0);
		sessionPicker.setMinValue(0);
		sessionPicker.setMaxValue(50);
		sessionPicker.setOnValueChangedListener(new SessionChooser());
		
		volDisp = (TextView) findViewById(R.id.textView3);
		
		masterSwitch = (Switch) findViewById(R.id.switch1);
		masterSwitch.setOnCheckedChangeListener(new MasterSwitcher());
		
		scanHandler = new Handler();
		sessionIncrementer = new SessionIncrementer();

		scanButton = (Button) findViewById(R.id.button1);
		scanButton.setBackgroundColor(Color.LTGRAY);
		scanButton.setOnClickListener(new SessionScannerListener());

		guessHandler = new Handler();

		guessButton = (Button) findViewById(R.id.button2);
		guessButton.setBackgroundColor(Color.LTGRAY);
		guessButton.setOnClickListener(new SessionGuesserListener());

		silentPlayer = null;

		Log.e("nixonnet.musicvol", "Successfully initialized listeners");
	}

	//taking this idea from https://github.com/felixpalmer/android-visualizer/commit/a0227b318b91451daf3fd8d23fb1a5c44a4e187d by h6ah4i
	protected void onResume(){
		super.onResume();
		if(silentPlayer != null){
			silentPlayer.release();
		}
		silentPlayer = MediaPlayer.create(getApplicationContext(), R.raw.silent);
		silentPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
	}
	
	public void setVolume(short value){//value is a percent
		try{
			//maximum is usually 100%
			short level =  (short)(volumeLimits[0] + (short)((volumeLimits[1]-volumeLimits[0])*(value/100.0f)));
			Log.e("nixonnet.musicvol", "Setting volume to " + value + "% " + level + " of " + volumeLimits[1]);
			eq.setEnabled(true);
			short numbands = eq.getNumberOfBands();
			for(short band = 0; band < numbands; band++){
				//short bandLevel = (short)(level+initialValues[band]);
				short bandLevel = level;
				eq.setBandLevel(band, bandLevel);
			}
		}
		catch(Exception e){
			//this is normal because we're guessing on session ID
		}
	}
	
	public void setAudioSession(int sessionNum){
		Log.e("nixonnet.musicvol", "Setting session " + sessionNum);
		try{
			eq.release();
		}catch(Exception e){}//if release fails then creation probably had also failed
		try{
			eq = new Equalizer(1000, sessionNum);
			volumeLimits = eq.getBandLevelRange();
			initialValues = new short[eq.getNumberOfBands()];
			for(short band = 0; band < eq.getNumberOfBands(); band++){
				initialValues[band] = eq.getBandLevel(band);
			}
			setVolume((short) volSlider.getProgress());
		}
		catch(Exception e){
		}
	}
	
	public class MasterSwitcher implements CompoundButton.OnCheckedChangeListener {
		@Override 
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if(!isChecked){
				try{
					eq.release();
				} catch(Exception e){ }
			} else {
				//setAudioSession(sessionPicker.getValue());
			}
		}
	}
	
	public class SessionIncrementer implements Runnable {
		@Override
		public void run() {
			int currentSession = sessionPicker.getValue();
			if(currentSession == sessionPicker.getMaxValue()){
				sessionPicker.setValue(sessionPicker.getMinValue());
			} else {
				sessionPicker.setValue(currentSession + 1);
				setAudioSession(currentSession + 1);
			}
			scanHandler.postDelayed(this, 1000);
		}
	}
	
	public void toggleScanState(){
		if(!scanning){
			scanning = true;
			scanHandler.postDelayed(sessionIncrementer, 0);
			scanButton.setText("That's It!");
			scanButton.setBackgroundColor(Color.GREEN);
		} else {
			scanning = false;
			scanHandler.removeCallbacks(sessionIncrementer);
			scanButton.setText("Scan");
			scanButton.setBackgroundColor(Color.LTGRAY);
		}
	}

	public class SessionScannerListener implements Button.OnClickListener {
		@Override
		public void onClick(View v) {
			toggleScanState();
		}
	}

	public class SessionGuesserTask implements Runnable {
		int sessionNum;
		public SessionGuesserTask(int sessionNum){
			this.sessionNum = sessionNum;
			Log.e("nixonnet.musicvol","Created guesser for session " + sessionNum);
		}
		@Override
		public void run() {
			if(!foundSession && sessionNum < 100 && masterSwitch.isChecked()){
				try {
					//Log.e("nixonnet.musicvol", "About to guess for session " + sessionNum);
					guessHandler.postDelayed(new SessionGuesserTask(sessionNum + 1), 1000);
					Visualizer listener = new Visualizer(sessionNum);
					listener.setEnabled(false);
					listener.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
					int retval = listener.setDataCaptureListener(new VisualizerListener(sessionNum, listener), Visualizer.getMaxCaptureRate() / 2, true, false);
					listener.setEnabled(true);
					Log.e("nixonnet.musicvol", "Got " + retval + " setting listener on " + sessionNum);
				} catch(Exception e){
					//if this doesn't work it probably wasn't the right session
					Log.e("nixonnet.musicvol", e.toString() + " from session " + sessionNum);
				}
			} else {//done
				guessButton.setText("Guess!");
				guessButton.setBackgroundColor(Color.LTGRAY);
			}
		}
	}

	public class SessionGuesserListener implements Button.OnClickListener {
		@Override
		public void onClick(View v) {
			if(!masterSwitch.isChecked())return;
			foundSession = false;
			guessHandler.postDelayed(new SessionGuesserTask(1), 1000);
			guessButton.setText("Thinking...");
			guessButton.setBackgroundColor(Color.GREEN);
		}
	}

	public class VisualizerListener implements Visualizer.OnDataCaptureListener {

		private int listeningSession;
		private Visualizer listeningVisualizer;

		public VisualizerListener(int listeningSession, Visualizer listeningVisualizer){
			//Log.e("nixonnet.musicvol", "Created visualizer listener for session " + listeningSession);
			this.listeningSession = listeningSession;
			this.listeningVisualizer = listeningVisualizer;
		}

		@Override
		public void onWaveFormDataCapture(Visualizer visualizer, byte[] audioBuffer, int samplingRate) {
			//Log.e("nixonnet.musicvol", "got data from session " + listeningSession);
			if(!foundSession) {
				int heardSomething = 0;
				for (int i = 0; i < 16; i++) {
					if (audioBuffer[i] != -128) heardSomething++;
				}
				if (heardSomething > 0) {
					Log.e("nixonnet.musicvol", "heard something on session " + listeningSession);
					foundSession = true;
					setAudioSession(listeningSession);
					sessionPicker.setValue(listeningSession);
				} else {
					Log.e("nixonnet.musicvol", "nothing on session " + listeningSession);
				}
				//remove self from listening
				visualizer.release();
			}
		}

		@Override
		public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
			//ignore
		}
	}

	public class VolumeAdjuster implements SeekBar.OnSeekBarChangeListener {
		@Override
		public void onProgressChanged(SeekBar slider, int value, boolean fromUser) {
			if(fromUser){
				setVolume((short)value);
				volDisp.setText(slider.getProgress()*5+"%");
			}
		}
		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
			//ignore
		}
		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			//ignore	
		}
	}
	
	public class SessionChooser implements NumberPicker.OnValueChangeListener {
		@Override
		public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
			if(!masterSwitch.isChecked())return;
			setAudioSession(newVal);
			if(scanning && newVal < oldVal){
				toggleScanState();
			}
		}
	}
}
