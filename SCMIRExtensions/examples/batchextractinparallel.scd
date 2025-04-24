//testing batch extraction in parallel

(
//wavs
~filenames = PathName("/data/audio/mirdata/EDM/Dance Decade later 1990s/").entries.collect{|p| p.fullPath};

//MP3s
//~filenames = ["Radiohead","Kirsty MacColl"].collect{|name|  PathName("/data/audio/mirdata/careercomparison/"++name++"/").entries.collect{|album| album.entries.collect{|p| p.fullPath}}.flatten; }.flatten;

// ~filenames = ["Radiohead","REM","Coldplay","Buddy Holly","PatsyCline","Kirsty MacColl"].collect{|name|  PathName("/data/audio/mirdata/careercomparison/"++name++"/").entries.collect{|album| album.entries.collect{|p| p.fullPath}}.flatten; }.flatten;

//if needed and precalculated
//SCMIR.loadGlobalFeatureNorms("/data/audio/mirdata/careercomparison/norms.scmirZ");

//may take a little longer than the completion function call due to sub forked processes
//default segmentation (feature means within each song)
SCMIR.batchExtractParallel(~filenames,[[MFCC, 13], [Chromagram, 12]],false, false, 0, oncompletion:{|durations, output| ~durations = durations; ~featuremeans = output; });

//if using global normalisation via SCMIR.globalfeaturenorms
// ~normmode = 0;
// SCMIR.batchExtractParallel(~filenames,[[MFCC, 13], [Chromagram, 12]],true, true, ~normmode, oncompletion:{|durations, output| ~durations = durations; ~featuremeans = output; });

)

~durations.sum/60 //total duration of audio touched in minutes
~featuremeans.size //one per song
~filenames.size
~featuremeans[2][0].plot //plot mean feature vector for first song (only segmentation, hence [0])








//global normalisation calculation
(
~normmode = 0; //max-min normalisation

//wavs
~filenames = PathName("/data/audio/mirdata/EDM/Dance Decade later 1990s/").entries.collect{|p| p.fullPath};

SCMIR.findGlobalFeatureNormsParallel(~filenames,[[MFCC, 13], [Chromagram, 12]],~normmode);

)


SCMIR.globalfeaturenorms









//prototyping, now wrapped in global function in SCMIR class
//global normalisation calculation
(
~normmode = 0; //max-min normalisation

//wavs
~filenames = PathName("/data/audio/mirdata/EDM/Dance Decade later 1990s/").entries.collect{|p| p.fullPath};

/*	*batchExtractParallel {|filenames, features, normalize=true, useglobalnormalization=false,normalizationmode=0, whichchannel, segmentations, oncompletion|*/

// 1 max, 2 min so [[[0.0],1],[[0.0],2]] does two segmentations, taking overall max and min for each feature within a given song
//segmentations:[[[0.0],1],[[0.0],2]]
SCMIR.batchExtractParallel(~filenames,[[MFCC, 13], [Chromagram, 12]],true, false, ~normmode,segmentations:[nil], oncompletion:{|durations, output, framesum|

	[\durations,durations].postln;
	[\output,output].postln;

	//to remove array of segmentations since didn't need it
	//output = output.collect{|val| val[0]};

	~durations = durations;
	~output = output.collect{|val| val[0]};
	~framesum = framesum;

	//~norms = SCMIR.findGlobalFeatureNorms([0,~output,~durations,framesum]);
	SCMIR.findGlobalFeatureNorms([0,~output,~durations,~framesum],nil,~normmode);

},normstats:true);


)







