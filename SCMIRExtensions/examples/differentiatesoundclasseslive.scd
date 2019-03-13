//classify voice vs solo piano (but could be anything else)

(
~filenames = [
	"/data/teachingmaterials/electronicmusic/samplingandcopyright/plunderphonics/69:96/27. case of death pt1.mp3",
"/data/teachingmaterials/electronicmusic/samplingandcopyright/plunderphonics/69:96/29. case of death pt.2.mp3",	"/data/audio/classical/improv/improv1.wav","/data/audio/classical/improv/20150227_Graphical_Score_2.wav"
];

~class = [0,0,1,1];

//could use more features, will need to update num inputs and hidden units for neural net below
~features = [SpecCentroid,SpectralEntropy,AttackSlope,[\CustomFeature,{arg input; //low, mid, high energy comparisons

		[
			RunningSum.rms(BLowPass4.ar(input,400,0.5),1024),
			RunningSum.rms(BBandPass.ar(input,3000,1),1024),
			RunningSum.rms(BHiPass4.ar(input,6000,0.5),1024)
		]

		},3
	]];


//for meaningful comparison between audio files, need to get global data on max and min values for features over all files
{
	~durations = SCMIR.findGlobalFeatureNorms(~filenames,~features);

	~featurenorms = SCMIR.globalfeaturenorms;
}.fork;

)



(

var windowsize = 2.0;
var stepsize = 1.0;

~featuremeansperwindow = nil!(~filenames.size);

{

	//.copyRange(0,1)
	~extractions = ~filenames.collect{|filename,i|

		var segmentsarray;
		[i,filename].postln;

		f = SCMIRAudioFile(filename,~features);

		//using global normalisation just set up
		f.extractFeatures(true,true);

		segmentsarray = Array.fill(((f.duration-windowsize - 0.001)/stepsize).floor,{|i| var pos = stepsize*i; [pos,pos+windowsize]  });

		~featuremeansperwindow[i] = f.gatherFeaturesBySegments(segmentsarray,false,0); //mean in each segment

	};



}.fork;

)

//~featuremeansperwindow.size

//now have the data to train a classifier

(
~featuremeansperwindow2 = ~featuremeansperwindow.collect{|array| array.clump(6)};

~data = ~featuremeansperwindow2.collect{|array,i| array.collect{|features| [features,[~class[i]]]}}.flatten;

~data = ~data.scramble; //randomise order

//basic 50/50 split
~trainingdata = ~data.copyRange(0,~data.size.div(2)-1);
~testdata = ~data.copyRange(~data.size.div(2),~data.size);

//6 features, 2 classes
//n = NaiveBayes(6,2);

n = NeuralNet(6,6,1);

n.trainExt(~trainingdata,0.02,1000);
)


//how well does the classifier perform?
(
~trainingscore = 0;

~trainingdata.do{|val| var output = n.calculate(val[0]).round(1).asInteger;

	if(val[1]==output){~trainingscore = ~trainingscore + 1; };};

[\trainingscore,~trainingscore,~trainingdata.size,~trainingscore/~trainingdata.size].postln;

~testscore = 0;

~testdata.do{|val| var output = n.calculate(val[0]).round(1).asInteger;

	if(val[1]==output){~testscore = ~testscore + 1; };};

[\testscore,~testscore,~testdata.size,~testscore/~testdata.size].postln;

""
)


//now deploy live


(
s.waitForBoot({
l = SCMIRLive(SCMIRAudioFile(~filenames[0],~features),~featurenorms);

l.createSynthDef.add;

	s.sync;

~halfstoresize = SCMIR.framerate.floor;
~storesize = ~halfstoresize * 2;
~store = {0!6}!~storesize;
~storecounter = 0;

l.createResponder({arg input;
	~storecounter= (~storecounter+1)%~storesize;
	~store[~storecounter] = input.copyRange(3,8);

	if(~storecounter%~halfstoresize==0) {

		//recalculate mean, use this for decision
		~meannow = ~store.mean;

		["speech","piano"].at(n.calculate(~meannow).round(1).asInteger).postln;

	};

}); //create a language side responder for this feature extractor


//run Synth with the SynthDef already added, input bus is 8, which is first audio input on machine
a = l.run(8)

});
)


//use SCMIR.saveArchive or save on the neural net or SCMIRLive object to save data for future recall

//store for future use (assumes saved SynthDef as well, or at least will createSynthDef on reload before live use)
l.save("/tmp/scmirlive1.scmirZ");

l.load("/tmp/scmirlive1.scmirZ");











