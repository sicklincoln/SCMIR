
+ SCMIR {

	*batchExtractParallel {|filenames, features, normalize=true, useglobalnormalization=false,normalizationmode=0, saveoutput=false, summarytype=0, oncompletion|

		var spawnNRT;
		var pid;
		var numactive = 0;
		var maxactive = SCMIR.numcpus;
		var numtorender;
		var numrendered = 0;
		var time = Main.elapsedTime;
		var increment = 0;
		var outputdata;
		var durations;

		//summary types
		//0 mean, 1 max, 2 min, 3 stddev


		numtorender = filenames.size;

		if(saveoutput) {
		outputdata = {}!numtorender;
		};
		durations = {}!numtorender;


		spawnNRT = {|action, whichspawn|

			var e;
			var whichnow = whichspawn -1;

			//[\spawnNRT,whichspawn,~filenames[whichspawn-1]].postln;

			//e = SCMIRAudioFile(Platform.resourceDir +/+ "sounds/a11wlk01.wav", [[MFCC, 13], [Chromagram, 12]]);

			e = SCMIRAudioFile(filenames[whichnow], features, normalizationmode);

			durations[whichnow] = e.duration;

			//shortcut versions also work, defaults will be applied for MFCC (10 coeffs) and Chromagram (12TET)
			//e = SCMIRAudioFile(Platform.resourceDir +/+"sounds/a11wlk01.wav",[MFCC,Chromagram]);

			e.extractFeaturesParallel(normalize,useglobalnormalization,nil,{|result, pid, saf|

				//[result,pid].postln;

				action.(result, pid, saf);

				if(saveoutput) {
					outputdata[whichnow] = e.gatherFeaturesBySegments([0.0],false,summarytype); };

				[\finished,whichspawn, \after, Main.elapsedTime - time].postln;

			},whichspawn);

		};



		{

			//var accumulatepids = List[];

			while({numrendered<numtorender},{

				if(numactive<maxactive,{

					numrendered = numrendered + 1;

					numactive = numactive + 1;

					//[\beforespawnNRTcall,numrendered].postln;

					spawnNRT.({|result, pid, scmiraudiofile|

						numactive = numactive - 1;
						//accumulatepids.add(pid); ~accumulatepids = accumulatepids;
						//[\numfeaturescheck, scmiraudiofile.numfeatures].postln;

						//scmiraudiofile

				},numrendered) });

				0.01.wait;
				//1.wait;
			});

			[\totaltime, Main.elapsedTime - time].postln;

			oncompletion.(durations,outputdata);

		}.fork;

	}



}





