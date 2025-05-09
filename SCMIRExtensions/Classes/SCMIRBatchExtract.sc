
+ SCMIR {

	//classvar outputbackup;

	//run normalization procedures for all standard features over all filenames in list, in parallel
	*findGlobalFeatureNormsParallel {|filenamelist, featureinfo, normalizationtype=0,filestart=0,filedur=0,numquantiles=10,whichchannel|

		SCMIR.batchExtractParallel(filenamelist,featureinfo,true, false, normalizationtype,whichchannel, segmentations:[nil], oncompletion:{|durations, output, framesum|

			//to remove array of segmentations since didn't need it
			output = output.collect{|val| val[0]};

			SCMIR.findGlobalFeatureNorms([0,output,durations,framesum],nil,normalizationtype,numquantiles:numquantiles);

		},normstats:true,filestart:filestart,filedur:filedur);

	}



	*batchExtractParallel {|filenames, features, normalize=true, useglobalnormalization=false,normalizationmode=0, whichchannel, segmentations, oncompletion, normstats=false,filestart=0,filedur=0|

		var spawnNRT;
		var pid;
		var numactive = 0;
		var maxactive = SCMIR.numcpus;
		var numtorender;
		var numscheduled = 0;
		var numrendered = 0;
		var time = Main.elapsedTime;
		var increment = 0;
		var outputdata;
		var durations;
		var framesum;


		[\normstats,normstats].postln;


		framesum = 0; //needed for some normalisation operations, so just calculate it anyway

		segmentations = segmentations ?? {[[[0.0],0]]};

		//summary types
		//0 mean, 1 max, 2 min, 3 stddev


		numtorender = filenames.size;

		//if(saveoutput) {
		outputdata = {}!numtorender;
		//};
		durations = {}!numtorender;


		spawnNRT = {|action, whichspawn|

			var e;
			var whichnow = whichspawn -1;

			//[\spawnNRT,whichspawn,~filenames[whichspawn-1]].postln;

			//e = SCMIRAudioFile(Platform.resourceDir +/+ "sounds/a11wlk01.wav", [[MFCC, 13], [Chromagram, 12]]);

			e = SCMIRAudioFile(filenames[whichnow], features, normalizationmode,filestart,filedur);

			durations[whichnow] = e.duration;

			//shortcut versions also work, defaults will be applied for MFCC (10 coeffs) and Chromagram (12TET)
			//e = SCMIRAudioFile(Platform.resourceDir +/+"sounds/a11wlk01.wav",[MFCC,Chromagram]);

			e.extractFeaturesParallel(normalize,useglobalnormalization,whichchannel,{|result, pid, saf|

				//[result,pid].postln;

				framesum = framesum + e.numframes;

				//if(saveoutput) {

				outputdata[whichnow] = segmentations.collect{|segmentation| var segments,summarytype;

					//return raw feature data if nil, else find an average or other stat
					if(segmentation.isNil) {e.featuredata; } {
						segments = segmentation[0]; summarytype = segmentation[1];  e.gatherFeaturesBySegments(segments,false,summarytype);
					};

				};


				action.(result, pid, saf);


				//	};

				[\finished,whichspawn, \after, Main.elapsedTime - time].postln;

			},whichspawn,normstats);

		};



		{

			//var accumulatepids = List[];

			while({numscheduled<numtorender},{

				if(numactive<maxactive,{

					numscheduled = numscheduled + 1;

					numactive = numactive + 1;

					//[\beforespawnNRTcall,numrendered].postln;

					spawnNRT.({|result, pid, scmiraudiofile|

						numactive = numactive - 1;
						//accumulatepids.add(pid); ~accumulatepids = accumulatepids;
						//[\numfeaturescheck, scmiraudiofile.numfeatures].postln;

						//scmiraudiofile

						numrendered = numrendered + 1;

						//when finally finished
						if(numrendered>=numtorender)
						//if(numrendered == numtorender)
						{

							[\totaltime, Main.elapsedTime - time].postln;
							oncompletion.(durations,outputdata, framesum);
						};


				},numscheduled) });

				0.01.wait;
				//1.wait;
			});


		}.fork;

	}



}





