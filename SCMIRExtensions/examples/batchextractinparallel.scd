//testing batch extraction in parallel

//TODO: SCMIR.findGlobalFeatureNormsParallel
//TODO gatherFeaturesBySegments options

(
//wavs
~filenames = PathName("/data/audio/mirdata/EDM/Dance Decade later 1990s/").entries.collect{|p| p.fullPath};

//MP3s
//~filenames = ["Radiohead","Kirsty MacColl"].collect{|name|  PathName("/data/audio/mirdata/careercomparison/"++name++"/").entries.collect{|album| album.entries.collect{|p| p.fullPath}}.flatten; }.flatten;

// ~filenames = ["Radiohead","REM","Coldplay","Buddy Holly","PatsyCline","Kirsty MacColl"].collect{|name|  PathName("/data/audio/mirdata/careercomparison/"++name++"/").entries.collect{|album| album.entries.collect{|p| p.fullPath}}.flatten; }.flatten;

//if needed and precalculated
//SCMIR.loadGlobalFeatureNorms("/data/audio/mirdata/careercomparison/norms.scmirZ");

//may take a little longer than the completion function call due to sub forked processes
SCMIR.batchExtractParallel(~filenames,[[MFCC, 13], [Chromagram, 12]],true, false, 0, true, 0, {|durations, featuremeans| ~durations = durations; ~featuremeans = featuremeans; });

)

~durations.sum/60 //total duration of audio touched in minutes
~featuremeans.size
~filenames.size
~featuremeans[0].plot


