/***************************************************************************
*                                                                          *
* Panako - acoustic fingerprinting                                         *
* Copyright (C) 2014 - 2017 - Joren Six / IPEM                             *
*                                                                          *
* This program is free software: you can redistribute it and/or modify     *
* it under the terms of the GNU Affero General Public License as           *
* published by the Free Software Foundation, either version 3 of the       *
* License, or (at your option) any later version.                          *
*                                                                          *
* This program is distributed in the hope that it will be useful,          *
* but WITHOUT ANY WARRANTY; without even the implied warranty of           *
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            *
* GNU Affero General Public License for more details.                      *
*                                                                          *
* You should have received a copy of the GNU Affero General Public License *
* along with this program.  If not, see <http://www.gnu.org/licenses/>     *
*                                                                          *
****************************************************************************
*    ______   ________   ___   __    ________   ___   ___   ______         *
*   /_____/\ /_______/\ /__/\ /__/\ /_______/\ /___/\/__/\ /_____/\        *
*   \:::_ \ \\::: _  \ \\::\_\\  \ \\::: _  \ \\::.\ \\ \ \\:::_ \ \       *
*    \:(_) \ \\::(_)  \ \\:. `-\  \ \\::(_)  \ \\:: \/_) \ \\:\ \ \ \      *
*     \: ___\/ \:: __  \ \\:. _    \ \\:: __  \ \\:. __  ( ( \:\ \ \ \     *
*      \ \ \    \:.\ \  \ \\. \`-\  \ \\:.\ \  \ \\: \ )  \ \ \:\_\ \ \    *
*       \_\/     \__\/\__\/ \__\/ \__\/ \__\/\__\/ \__\/\__\/  \_____\/    *
*                                                                          *
****************************************************************************
*                                                                          *
*                              Panako                                      *
*                       Acoustic Fingerprinting                            *
*                                                                          *
****************************************************************************/

package be.panako.ui;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.Map.Entry;

import javax.swing.SwingUtilities;

import be.panako.strategy.nfft.NFFTEventPoint;
import be.panako.strategy.nfft.NFFTFingerprint;
import be.panako.strategy.qifft.QIFFTEventPoint;
import be.panako.strategy.qifft.QIFFTEventPointProcessor;
import be.panako.strategy.qifft.QIFFTFingerprint;
import be.panako.util.Config;
import be.panako.util.Key;
import be.panako.util.StopWatch;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;

class QIFFTAudioFileInfo implements AudioProcessor{
	
	
	public final TreeMap<Float,float[]> magnitudes;

	private QIFFTEventPointProcessor eventPointProcessor;
	private AudioDispatcher d;
	private float runningMaxMagnitude;

	private final File audioFile;


	public List<QIFFTFingerprint> fingerprints;
	
	//matching with reference
	public final List<QIFFTEventPoint> matchingEventPoints;
	public final List<QIFFTFingerprint> matchingPrints;
	//time offset in seconds with respect to the reference
	private double timeOffset;
	
	private QIFFTAudioFileInfo referenceFileInfo;

	public List<QIFFTEventPoint> eventpoints;
	
	public QIFFTAudioFileInfo(File audioFile,QIFFTAudioFileInfo referenceFileInfo){
		this.audioFile = audioFile;
		magnitudes = new TreeMap<Float,float[]>();
		fingerprints = new ArrayList<>();
		eventpoints = new ArrayList<>();
		
		matchingPrints = new ArrayList<QIFFTFingerprint>();
		matchingEventPoints = new ArrayList<QIFFTEventPoint>();
		this.referenceFileInfo = referenceFileInfo;
	}
	
	public void extractInfoFromAudio(final Component componentToRepaint){
		int samplerate = Config.getInt(Key.NFFT_SAMPLE_RATE);
		int size = Config.getInt(Key.NFFT_SIZE);
		int overlap = size - Config.getInt(Key.NFFT_STEP_SIZE);
		StopWatch w = new StopWatch();
		w.start();
		
		d = AudioDispatcherFactory.fromPipe(audioFile.getAbsolutePath(), samplerate, size, overlap);
		eventPointProcessor = new QIFFTEventPointProcessor(size,overlap,samplerate,4);
		d.addAudioProcessor(eventPointProcessor);
		d.addAudioProcessor(this);
		d.addAudioProcessor(new AudioProcessor() {
			@Override
			public void processingFinished() {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						componentToRepaint.repaint();
					}
				});
				if(referenceFileInfo!=null)
					referenceFileInfo.setMatchingFingerprints(matchingPrints);
			}			
			@Override
			public boolean process(AudioEvent audioEvent) {
				return true;
			}
		});
		new Thread(d).start();
	}
	
	@Override
	public boolean process(AudioEvent audioEvent) {
		float[] currentMagnitudes = eventPointProcessor.getMagnitudes().clone();
		
		//for visualization purposes:
		//store the new max value or, decay the running max
		float currentMaxValue = max(currentMagnitudes);
		if(currentMaxValue > runningMaxMagnitude){
			runningMaxMagnitude = currentMaxValue;
		}else{
			runningMaxMagnitude = 0.9999f * runningMaxMagnitude;
		}
		normalize(currentMagnitudes);
		
		magnitudes.put((float)audioEvent.getTimeStamp(),currentMagnitudes);
		
		return true;
	}
	
	
	@Override
	public void processingFinished() {
		this.eventpoints = eventPointProcessor.getEventPoints();
		this.fingerprints = eventPointProcessor.getFingerprints();	
		HashMap<Integer, Integer> mostPopularTimeOffsetCounter = new HashMap<>();
		
		if(referenceFileInfo!=null){
			for(QIFFTFingerprint otherPrint : referenceFileInfo.fingerprints){
				for(QIFFTFingerprint thisPrint : this.fingerprints){
					if(thisPrint.hashCode()==otherPrint.hashCode()){
						matchingPrints.add(thisPrint);
						int timeDiff = (int) (otherPrint.t1-thisPrint.t1);
						if(!mostPopularTimeOffsetCounter.containsKey(timeDiff)){
							mostPopularTimeOffsetCounter.put(timeDiff, 0);
						}
						mostPopularTimeOffsetCounter.put(timeDiff, mostPopularTimeOffsetCounter.get(timeDiff)+1);
					}
				}
			}
			
			int maxAlignedFingerprints = -1;
			int bestOffset = -1;
			for(Entry<Integer,Integer> entry : mostPopularTimeOffsetCounter.entrySet()){
				if(entry.getValue()>maxAlignedFingerprints){
					maxAlignedFingerprints = entry.getValue();
					bestOffset = entry.getKey();
				}
			}

			timeOffset = bestOffset * Config.getInt(Key.NFFT_STEP_SIZE)/  ((float) Config.getInt(Key.NFFT_SAMPLE_RATE));
			System.out.println("Found a time offset of " + timeOffset);
		}
		
				
		
	}
	
	public double getTimeOffset(){
		return timeOffset;
	}
	
	public File getFile(){
		return audioFile;
	}
	
	private float max(float[] magnitudes){
		float max = 0;
		for(int i = 0 ; i < magnitudes.length ;i++){
			if(magnitudes[i]!=0){
				max = Math.max(max, magnitudes[i]);
			}
		}
		return max;
	}
	
	/**
	 * Normalizes the magnitude values to a range of [0,1].
	 */
	private void normalize(float[] magnitudes){
		for(int i = 0 ; i < magnitudes.length ;i++){
			if(magnitudes[i]!=0){
				magnitudes[i] = magnitudes[i]/runningMaxMagnitude;
			}
		}
	}

	public void setMatchingFingerprints(List<QIFFTFingerprint> matchingPrints2) {
		matchingPrints.clear();
		HashMap<Integer,QIFFTFingerprint> prints = new HashMap<Integer, QIFFTFingerprint>();
		for(QIFFTFingerprint print : fingerprints){
			prints.put(print.hash(), print);
		}
		for(QIFFTFingerprint print : matchingPrints2){
			if(prints.containsKey(print.hash())){
				matchingPrints.add(prints.get(print.hash()));
			}
		}
	}
	
}
