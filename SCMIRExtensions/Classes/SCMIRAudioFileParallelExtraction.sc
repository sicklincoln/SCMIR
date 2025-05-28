//extract as fast as possible, with completion function for unixCmd

+ SCMIRAudioFile {
	//must be called within a fork? How to enforce, test that?
	extractFeaturesParallel {|normalize=true, useglobalnormalization=false, whichchannel, completionFunction, uniquenum, normstats=false|

		var onunixcmdcompletion;

		var featurehop = SCMIR.framehop; //1024; //measurement about 40Hz

		var score;
		var serveroptions, buffersize;
		var temp;
		var normdata;
		var ugenindex;
		var file;
		var def, defname, defpath;

		("Extracting features for"+sourcepath).postln;


		//[\normstats2,normstats].postln;





		uniquenum = uniquenum ?? {SCMIR.nrtanalysisuniquenum = (SCMIR.nrtanalysisuniquenum + 1)%(SCMIR.nrtanalysisuniquenumMax); SCMIR.nrtanalysisuniquenum};

		defname = \SCMIRAudioFileFeatures ++ uniquenum;

		defpath = (SynthDef.synthDefDir ++ defname ++ ".scsyndef").asUnixPath;

		 //safety if called multiple times and switched to beats later
		//featuresforbeats = false;
		featuresbysegments = false;

		//mono input typically (can load stereo or above but assumes later feature extractors reduce from multichannel to less size output)
		def = SynthDef(defname,{arg playbufnum, length;
			var env, input, features, trig;
		//	var , chain, centroid, ;
		//	var mfccfft, chromafft, specfft, onsetfft;
			var featuresave;

			env=EnvGen.ar(Env([1,1],[length]),doneAction:2);
			//stereo made mono
			input= if(whichchannel.isNil,{if(numChannels==1,{
				PlayBuf.ar(1, playbufnum, BufRateScale.kr(playbufnum), 1, 0, 0);
				},{

				Mix(PlayBuf.ar(numChannels, playbufnum, BufRateScale.kr(playbufnum), 1, 0, 0))/numChannels;

			});
				},{
				//choice of channel
					//PlayBuf.ar(numChannels, playbufnum, BufRateScale.kr(playbufnum), 1, 0, 0)[whichchannel]


				if(whichchannel>=0) {
				//buffer only loaded a specific channel already
				PlayBuf.ar(1, playbufnum, BufRateScale.kr(playbufnum), 1, 0, 0);
					}
					{ //else multichannel mode, must be compatible with later feature extractors else will be trouble... assumes render from multiple inputs to one output stream

PlayBuf.ar(whichchannel.neg, playbufnum, BufRateScale.kr(playbufnum), 1, 0, 0);

					}
			});

			//get features

			//ASSUMES SR of 44100 or 48000
			#features, trig = SCMIRAudioFile.resolveFeatures(input,featurehop,featureinfo);

			//[\sizecheck, features.size].postln;

			//Logger.kr(features, trig, loggerbufnum);
			featuresave = FeatureSave.kr(features, trig);

			 //issue in that doesn't seem to correspond to necessary unit index
			//ugenindex =  featuresave.synthIndex;
			//must check post hoc, because of optimisation changes

			//[\ugenindex, ugenindex].postln;

			//no actual output required, goes via logger buffer

		});

		//find synth index for FeatureSave

		def.children.do{|val,i| if(val.class==FeatureSave,{ugenindex = val.synthIndex})};

		def.writeDefFile;


		//for batch processing, need this fork outside;

		//wait for SynthDef sorting just in case
		//0.1.wait;

		//SCMIR.waitIfRoutine(0.1);

		//analysisfilename = (analysispath++basename++(uniquenum.asString)++"features.data").asUnixPath;

		//safer since some filenames a bit weird and might be calling asUnixPath twice leading to all sorts of bad grammar...
		analysisfilename = (analysispath++"parallelNRT"++(uniquenum.asString)++"features.data"); //.asUnixPath;

		[\analysisfilename, analysisfilename, basename, uniquenum.asString].postln;

		//analysisfilename= analysispath++basename++"features.wav";
		//analysisfilename.postln;

		//allow for 10 beats per second, else unreasonable, 2 floats per beat= [beat time, curr tempo estimate]
		//windows per second * length . numfeatures is number of channels in buffer?
		//initial delay in FFT implementation is hopsize itself.
		buffersize= ((44100*duration)/featurehop).asInteger; //+1 for safety not needed unless rounding error on exact match

		score = [

		//after path, next two arguments: int - starting frame in file (optional. default = 0) int - number of frames to read (optional. default = 0, see below)

		//[0.0, [\b_allocRead, 0, sourcepath, 0, 0]],

			if(whichchannel.isNil,{[0.0, [\b_allocRead, 0, sourcepath, loadstart, loadframes]]},{

				if(whichchannel>=0) {

			[0.0, [\b_allocReadChannel, 0, sourcepath, loadstart, loadframes, whichchannel]]
				} {
					//if multichannel load, take as many channels as in sound file, nothing fancy
			[0.0, [\b_allocRead, 0, sourcepath, loadstart, loadframes]]
				}

			}),   //loadframes

		[0.0, [ \s_new, defname, 1000, 0, 0,\playbufnum,0,\length, duration]], //plus any params for fine tuning
		//[0.0,[\u_cmd, 1000, ugenindex, "createfile",analysisfilename]],
		[0.01,[\u_cmd, 1000, ugenindex, "createfile",analysisfilename]], //can't be at 0.0, needs allocation time for synth before calling u_cmd on it
		//[0.0, [\b_alloc, 1, buffersize, numfeatures.postln]],
		//[0.0, [ \s_new, \SCMIRAudioFileFeatureExtraction, 1000, 0, 0,\playbufnum,0,\loggerbufnum,1,\length, duration]], //plus any params for fine tuning

		//after length of soundfile played, end
		//[duration,[\b_write,1,analysisfilename,"WAV", "float"]],
		[duration,[\u_cmd, 1000, ugenindex, "closefile"]],
		[duration, [\c_set, 0, 0]]
		];

		serveroptions = ServerOptions.new;
		serveroptions.numOutputBusChannels = 1; // mono output
		//serveroptions.numOutputBusChannels
		serveroptions.verbosity = -2;
		//serveroptions.memSize = 81920*4;
		//serveroptions.numBuffers = 2048;
		//serveroptions.numWireBufs = 256;

		//serveroptions.maxSynthDefs = 2048;

		{


		onunixcmdcompletion = {|result, pid|

			{

			//[\onunixcmdcompletion, analysisfilename].postln;

			//safety
			0.1.wait;

			//kill def file to avoid accumulation

			("ls -al "++ defpath).unixCmd;
			//("rm "++ defpath).postcs;


			//further safety delay...
			{

				10.0.wait;

			("rm "++ defpath).unixCmd;
					//"/Users/ioi/Library/Application Support/SuperCollider/synthdefs/SCMIRAudioFileFeatures7.scsyndef".asUnixPath

			//SynthDescLib.hglobal.at(defname);
			//SynthDescLib.global.at("SCMIRAudioFileFeatures27")
			SynthDescLib.global.removeAt(defname);

			}.fork;

		//LOAD FEATURES


		[\analysisfilenamesanitycheck,analysisfilename].postcs;

		//Have to be careful; Little Endian is standard for Intel processors
		file = SCMIRFile(analysisfilename,"rb");

		numframes = file.getInt32LE;

		//[\numframes,numframes].postln;

		temp = file.getInt32LE;
		if (numfeatures!= temp) {
			"extract features: mismatch of expectations in number of features ".postln;
			[numfeatures, temp].postln;
		};

		temp = numframes*numfeatures;
		featuredata = FloatArray.newClear(temp);

//		temp.do{|i|
//
//			featuredata[i] = file.getFloatLE;
//
//		};

		//faster implementation?
		file.readLE(featuredata);

		if((featuredata.size) != temp) {

			file.close;

			featuredata = nil;

			SCMIR.clearResources;

			file = SCMIRFile(analysisfilename,"rb");
			file.getInt32LE;
			file.getInt32LE;

			onsetdata = FloatArray.newClear(temp);
			file.readLE(featuredata);  //should be onsetdata?
		};


		file.close;

		if(normalize && (numframes>=1)){

						//[\normstats3,normstats].postln;

						if(normstats) {

							//norms[j] = [e.normalize(e.featuredata, true), e.numframes];
							featuredata = [this.normalize(featuredata,normstats),numframes];

						//[\normstats4,featuredata].postln;

						} {

						//check use of useglobalnom
							featuredata = this.normalize(featuredata,normstats,useglobalnormalization);

						};

						};



		//temp= SCMIR.soundfile.numChannels;

		//SCMIR.soundfile.close;

		//("rm "++ (analysisfilename.asUnixPath)).systemCmd;

		"Feature extraction complete".postln;

		completionFunction.(result,pid,this); //does nothing if nil

			}.fork;

		};



		//can set how verbose to be?

		//"NRTanalysis.wav"
		//issue with Score under 3.4 that it doesn't accept a nil argument for output?

		0.1.wait;

		//had oscFile location as "NRTanalysis", doesn't work on Linux due to write permissions
		Score.recordNRTSCMIRParallel(score,SCMIR.nrtanalysisfilename++uniquenum,SCMIR.nrtoutputfilename, nil,44100, "WAV", "int16", serveroptions,"",nil,onunixcmdcompletion); // synthesize
		//Score.recordNRT(score, "NRTanalysis", "NRToutput", nil,44100, "WAV", "int16", serveroptions); // synthesize

		//SCMIR.processWait("scsynth");

		}.fork;


	}



}





