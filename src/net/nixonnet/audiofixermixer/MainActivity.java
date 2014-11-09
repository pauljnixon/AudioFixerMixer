package net.nixonnet.audiofixermixer;

import android.app.Activity;
import android.graphics.Color;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.CompoundButton;;

public class MainActivity extends Activity {

	private SeekBar volSlider;
	private NumberPicker sessionPicker;
	private Equalizer eq;
	private short volumeLimits[] = {-500,500};
	private short initialValues[];
	private TextView volDisp;
	private Switch masterSwitch;
	private Button scanButton;
	private Handler scanHandler;
	private Runnable sessionIncrementer;
	private boolean scanning;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		volSlider = (SeekBar) findViewById(R.id.seekBar1);
		volSlider.setMax(20);
		volSlider.setProgress(10);
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
	}
	
	public void setVolume(short value){
		try{
			//maximum is usually 200%
			short level =  (short)(volumeLimits[0] + (short)((volumeLimits[1]-volumeLimits[0])*(value/40.0f)));
			eq.setEnabled(true);
			short numbands = eq.getNumberOfBands();
			for(short band = 0; band < numbands; band++){
				short bandLevel = (short)(level+initialValues[band]);
				eq.setBandLevel(band, bandLevel);
			}
		}
		catch(Exception e){
			//this is normal because we're guessing on session ID
		}
	}
	
	public void setAudioSession(int sessionNum){
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
				setAudioSession(sessionPicker.getValue());
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
