//SCMIRAudioFile onset detection/event segmentation 
  
+  SCMIRAudioFile {  
	  
	  
	//for batch processing, needs to be forked outside
	//expose all the Onsets UGen parameters in case users want to try different settings      
	extractOnsets { |threshold=0.5, odftype=\rcomplex, relaxtime=1,floor=0.1, mingap=10, medianspan=11, whtype=1|    
		  
		var fftsize=512;    	//ASSUMES SR of 44100 or 48000    
		var analysisfilename;       
		var serveroptions;       
		var score;      
		var temp;       
		var normdata;       
		var ugenindex;     
		var file;     
		var def;  
			 
		"Extracting onsets".postln;  
		
		//mono input only       
		def = SynthDef(\SCMIRAudioOnset,{arg playbufnum, length;         
			var env, input;       
			var fft, trig;        
			var featuresave;     
			var onsetoutput;   
			  
			env=EnvGen.ar(Env([1,1],[length]),doneAction:2);		       
			//stereo made mono       
			input= if(numChannels==1,{      
				PlayBuf.ar(1, playbufnum, BufRateScale.kr(playbufnum), 1, 0, 0);      
				},{      
				Mix(PlayBuf.ar(numChannels, playbufnum, BufRateScale.kr(playbufnum), 1, 0, 0))/numChannels;        
			});      
			  
			fft = FFT(LocalBuf(fftsize,1),input); //(,wintype:1);       
			  
			onsetoutput = Onsets.kr(fft, threshold, odftype, relaxtime,floor, mingap, medianspan, whtype);  
			  
			trig=HPZ1.kr(onsetoutput)-0.1; //stupid onset detector stays on 1 for multiple frames!   
				 
			//Sweep will time over course of playback  
			featuresave = FeatureSave.kr(Sweep.kr(Impulse.kr(0),1.0), trig);           
			  
		}); //.writeDefFile;       
			 
		def.children.do{|val,i| if(val.class==FeatureSave,{ugenindex = val.synthIndex})}; 
		 
		def.writeDefFile;      
		  
		  
		//wait for SynthDef sorting just in case       
		//0.1.wait;  
		//SCMIR.waitIfRoutine(0.1);      
		  
		  
		analysisfilename= analysispath++basename++"onsets.data";     
		analysisfilename.postln;       
		  
		score = [        
		//[0.0, [\b_allocRead, 0, sourcepath, 0, 0]],   
		[0.0, [\b_allocRead, 0, sourcepath, loadstart, loadframes]],     
		[0.0, [ \s_new, \SCMIRAudioOnset, 1000, 0, 0,\playbufnum,0,\length, duration]], //plus any params for fine tuning      
		[0.005,[\u_cmd, 1000, ugenindex, "createfile",analysisfilename]], //can't be at 0.0, needs allocation time for synth before calling u_cmd on it    
		[duration,[\u_cmd, 1000, ugenindex, "closefile"]],       
		[duration, [\c_set, 0, 0]]        
		];       
		  
		serveroptions = ServerOptions.new;      
		serveroptions.numOutputBusChannels = 1; // mono output       
		 
		//Score.recordNRT(score, "NRTanalysis", "NRToutput", nil,44100, "WAV", "int16", serveroptions); // synthesize       
		//SCMIR.processWait("scsynth");      
		Score.recordNRTSCMIR(score,SCMIR.nrtanalysisfilename,SCMIR.nrtoutputfilename, nil,44100, "WAV", "int16", serveroptions); // synthesize
		  
		//LOAD FEATURES    
		//Have to be careful; Little Endian is standard for Intel processors    
		file = SCMIRFile(analysisfilename,"rb");    
		  
		numonsets = file.getInt32LE;     
		  
		temp = file.getInt32LE;    
		if (temp!= 1) {    
			"extract onsets: mismatch of expectations in number of features ".postln;    
			[1, temp].postln;     
		};     
		  
		temp = numonsets; //*numfeatures;    
		onsetdata= FloatArray.newClear(temp);       
		  
		  
		//faster implementation?   
		file.readLE(onsetdata);   
		  
		if((onsetdata.size) != temp) {

			file.close; 
			
			onsetdata=nil; 
			
			SCMIR.clearResources; 
							
			file = SCMIRFile(analysisfilename,"rb");   
			file.getInt32LE;
			file.getInt32LE;
			  
			onsetdata= FloatArray.newClear(temp);  
			file.readLE(onsetdata);  
		};   
  
		file.close;     
		  
		  
		("rm "++ (analysisfilename.asUnixPath)).systemCmd;        
		  
		
		"Onsets extracted".postln;	 
	}  
	  
	  
		 
	setOnsetData{|inputonsetdata|  
			 
		onsetdata = inputonsetdata;  
		numonsets = onsetdata.size;  
	}	 
		 
		 
	//in general may need offset times as well as onsets, left ambiguous for now 
	gatherFeaturesByOnsets {|replace=true, meanormax=0| 
			 
		if (featuredata.notNil) { 
				 
			if (onsetdata.notNil) { 
					 
				^this.gatherFeaturesBySegments(onsetdata, replace, meanormax);  
				} { 
					 
				"SCMIRAudioFile:gatherFeaturesByOnsets - no onsets extracted to act as segmentation guide".postln;	 
			}; 
			} { 
				 
			"SCMIRAudioFile:gatherFeaturesByOnsets - no feature extraction carried out yet!".postln;		 
		} 
	} 
		 
	//convert featuredata to match up to segments, segments array passed in gives segment start positions (ends assumed next segment) 
	gatherFeaturesBySegments {|segments, replace=true, meanormax=0, beatsegments=false| 
		  
		var pos = 0.0;  
		var timeperframe= SCMIR.hoptime; //0.023219954648526; //converting from 1024 point windows at 44100 SR  
		var framenow=0, framenext;  
		var averagevector;  
		var newfeaturedata; //one vector per segment  
		var temp;  
			 
		segmenttimes = segments.copy;  
		numsegments = segments.size;  
		newfeaturedata = FloatArray.newClear(numfeatures*numsegments); //one vector per segment (onset event, beat, section etc) 
		  
		//for each segment location, take max or average of featuredata featurewise following it and up to next segment start or track end 
		// 
		segments.do{|time, i| 
			var starttime, endtime;  
				 
			starttime= time; 
				 
			endtime = if (i< (numsegments-1))  
			{segments[i+1];} 
			{ 
				if(beatsegments) { 
					if(tempi.notNil){min(starttime+(tempi[i]),duration)}{duration};  
					} { 
					duration; 	 
				} 
			};  
				 
			//how many frames fit in?  
			framenow = (starttime/timeperframe).roundUp;  
			framenext =  (endtime/timeperframe).asInteger; //rounds down  
				
			if(framenext >= numframes) {
				
				framenext = numframes - 1;  
			};	
				 
			if(framenext<framenow) {framenext= framenow;};  
				 
			if(framenow >= numframes) { 
				framenow = numframes - 1;  
				framenext = numframes - 1;  
			}; 
		
			averagevector = 0.0.dup(numfeatures);  
				 
			pos = numfeatures*framenow; 
				 
			if (meanormax==0) { 
				//mean 
					 
				for(framenow, framenext, {|j| 
					 
					numfeatures.do{|k| 
							 
						averagevector[k] = averagevector[k] + featuredata[pos+k];  
					}; 
						 
					pos = pos + numfeatures;  
						 
				});  
					 
				averagevector = averagevector/(framenext-framenow+1);  
					 
			} 
				 
			{ 
				//max 
					 
				for(framenow, framenext, {|j| 
					 
					numfeatures.do{|k| 
							 
						averagevector[k] = max(averagevector[k],featuredata[pos+k]);  
					}; 
						 
					pos = pos + numfeatures;  
						 
				});  
					 
					 
			}; 
				 
			pos = numfeatures*i;  
				 
			numfeatures.do{|k| 
					 
				newfeaturedata[pos+k] = averagevector[k];  
					 
			} 
				 
			//while({(pos<endtime) || },{ 
				//				 
				//				pos = pos + timeperframe;  
			//			}); 
			//			 
				 
		}; 
			 
		if(replace) { 
			temp = featuredata;  
			featuredata = newfeaturedata;  
			numframes = numsegments;  
			featuresbysegments = true;  
			^temp; //return old 
			} { 
				 
			^newfeaturedata	 
		} 
			 
			 
	}	 
	  
	  
}  
 
