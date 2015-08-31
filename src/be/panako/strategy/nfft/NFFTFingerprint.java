/***************************************************************************
*                                                                          *
* Panako - acoustic fingerprinting                                         *
* Copyright (C) 2014 - 2015 - Joren Six / IPEM                             *
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




package be.panako.strategy.nfft;




/**
 * A fingerprint connects two event points in a spectrogram. The points are defined
 * by a time and frequency pair, both encoded with an integer. The frequency is defined by
 * the bin index in the spectrogram. The time is defined as the index of the block processed.
 * 
 * @author Joren Six
 */
public class NFFTFingerprint {
	
	public final int t1;
	public final int f1;
	
	public final int t2;
	public final int f2;
	
	public double energy;

	
	public NFFTFingerprint(int t1,int f1,int t2,int f2){
		this.t1 = t1;
		this.f1 = f1;
		
		this.t2 = t2;
		this.f2 = f2;
		
		assert t2 > t1;
	}	
	
	public NFFTFingerprint(NFFTEventPoint l1, NFFTEventPoint l2){
		this(l1.t,l1.f,l2.t,l2.f);
	}
	
	/**
	 * Calculate a hash representing this fingerprint.
	 * 
	 * @return a hash representing this fingerprint.
	 */
	public int hash(){
		//8 bits for the exact location of the frequency component
		int f = f1 & ((1<<8)-1);
		//8 bits for the frequency delta (not fully used?)
		int deltaF = Math.abs(f2 - f1);
		deltaF = deltaF & ((1<<8)-1);
		//6 bits for the time difference
		int deltaT = Math.abs(timeDelta()) & ((1<<7)-1);
		//In total the hash contains 8 + 8 + 6 bits == 22 bits (about 4 million values)
		int hash = (f<<15) + (deltaF<<7) + deltaT;
		if(f1>f2){
			hash = hash *-1;
		}
		return hash;
	}
	

	/**
	 * @param hash the hash to reverse
	 * @return an array of integers with [f1,df,dt].
	 */
	public static int[] reverseHash(int hash){
		int[] values = new int[3];
		int f1 = hash>>14;
		int df = (hash - f1 * (1<<14)) / (1<<6);
		int dt = (hash - f1 * (1<<14) - df * (1<<6));
		values[0]=f1;
		values[1]=df;
		values[2]=dt;
		return values;
	}
	
	public String toString(){
		return String.format("%d,%d,%d,%d,%d",t1,f1,t2,f2,hash());
	}
	
	public boolean equals(Object other){
	    if (other == null){
	    	return false;
	    }
	    if (other == this){
	    	return true;
	    }
	    if (!(other instanceof NFFTFingerprint)){
	    	return false;
	    }
	    NFFTFingerprint otherFingerprint = (NFFTFingerprint) other;
	    boolean sameHash = otherFingerprint.hash() == this.hash();
	    //if closer than 100 analysis frames (of e.g. 32ms), than hash is deemed the same).
	    boolean closeInTime = Math.abs(otherFingerprint.t1 - this.t1) < 100;
	    return sameHash && closeInTime;
	}
	
	
	/*
	 * This is not completely consistent with the expected hash code / equals
	 * behavior: It is very well possible that that two hashes collide, while
	 * the fingerprints are not equal to each other. Implementing hash code makes
	 * sure no identical fingerprints are added, but also that no collisions are
	 * allowed. Take care when using sets.
	 */
	public int hashCode(){
		//This is not completely consistent with the expected hash code / equals behavior:
		//It is very well possible that that two hashes collide, while the fingerprints are not equal to each other.
		//Implementing hash code makes sure no identical fingerprints are added, but also that no collisions are
		//allowed. Take care when using sets. 
		return hash();
	}

	/**
	 * The time delta between the first and last event.
	 * 
	 * @return The difference between t1 and t2, in analysis frames.
	 */
	public int timeDelta() {
		return t2 - t1;
	}
	
	public static void main(String... args){
		
		NFFTFingerprint firstPrint = new NFFTFingerprint(2424,28,2524,22);
		NFFTFingerprint otherPrint = new NFFTFingerprint(887,28,923,34);
		System.out.println(firstPrint + " " + otherPrint);		
		firstPrint = new NFFTFingerprint(18732,42,18799,28);
		otherPrint = new NFFTFingerprint(809,42,876,28);
		System.out.println(firstPrint + " " + otherPrint);
	}
}
