import java.util.*;
import java.io.*;

/**
 * Parts-of-Speech tag using Hidden Markov Models
 * @author Yakoob Khan and Aadil Islam 
 * Spring 2018
 */

public class HiddenMarkovModel {
	/*
	 * Takes in pathnames for sentences and tag files, returns list of transition and observation maps
	 */
	public static List<Map<String, Map<String,Double>>> training (String sentences, String tagInfo) throws IOException {
		// list holds two maps, one for transitions and the other for observations 
		List<Map<String, Map<String,Double>>> result = new ArrayList<Map<String, Map<String,Double>>>();
		
		// model: { pos -> {pos -> transition score} }
		Map<String, Map<String,Double>> model = new HashMap<String, Map<String,Double>>();	
		// initialize model with start key
//		model.put("#", new HashMap<String,Double>());
		// state: { pos -> {word -> observation score} }
		Map<String, Map<String,Double>> state = new HashMap<String, Map<String,Double>>();
		
		// reader1: reads tagInfo file
		BufferedReader reader1 = new BufferedReader(new FileReader(tagInfo));
		// reader2: reads sentences file
		BufferedReader reader2 = new BufferedReader(new FileReader(sentences));
	
		// hold current lines in each file 
		String tagLine;
		String wordLine;
		
		// go through every line 
		while((tagLine = reader1.readLine()) != null) {
			wordLine = reader2.readLine();
			String[] words = wordLine.split(" "); 		// split the sentence into separate words	
			String[] tags = tagLine.split(" ");		   // split the tagLine into separate words
			
			for(int i = 0; i<tags.length; i++) {		// Loop through each tag
				// update start key
				if (i==0) {		
					if(model.containsKey("#")) {		// if model contains start vertex
						if(model.get("#").containsKey(tags[i])) {	// if edge is present
							double value = model.get("#").get(tags[i]);	// get the transition value
							model.get("#").put(tags[i], value + 1.0);	// update value by 1
						} else {	
							model.get("#").put(tags[i], 1.0);	// create a new edge with transition score 1
						}
					} else {		// if start key not present, create start key with this part of speech 
						model.put("#", new HashMap<String, Double>());
						model.get("#").put(tags[i], 1.0);
					}
					
				}
				// Update transitions
				if (i < tags.length-1) {		
					String key = tags[i]; 	//using this tag as key
					if(model.containsKey(key)) {		// check if model has this key
						if(model.get(key).containsKey(tags[i+1])) {	// check if model has the next key
							double value = model.get(key).get(tags[i+1]);	// retrive the value
							model.get(key).put(tags[i+1], value+1.0); 		//add 1, put back into map
						} 
						else {	// if edge with next tag not present 
							model.get(key).put(tags[i+1], 1.0);
						}
					} 
					else {	// if current tag not in transitions map
						model.put(key, new HashMap<String, Double>());
						model.get(key).put(tags[i+1], 1.0); //setting the tag's (current State's) next state's freq to 1.0
					}
				}
				// Update observations map with this word
				String word = words[i].toLowerCase();	// convert to lowercase
				if(!state.containsKey(tags[i])) {	// check if the state map contains this tag and update count
					state.put(tags[i], new HashMap<String, Double>());	// create element in state map with tag
					state.get(tags[i]).put(word, 1.0);	// initialize word freq to 1.0
				}	
				else {
					Map<String, Double> out = state.get(tags[i]);	// hold map of out-neighbors to tag
					if(out.containsKey(word)) {		// if word is already a key in out map, then increment freq
						out.put(word, out.get(word)+1);
					}
					else {		// otherwise if word is not in out map, initialize freq to 1.0
						out.put(word, 1.0);		
					}	
				}
			}
		}
		reader1.close();		// close readers
		reader2.close();
		result.add(model);	// add transitions and observations maps to result list
		result.add(state);
		convert2Log(result);		// convert all freq's from double to log, for floating point precision
		return result;
	}
	
	/*
	 * Helper function to convert double to log
	 */
	public static void convert2Log(List<Map<String, Map<String,Double>>> result){
		Map<String, Map<String, Double>> transitions = result.get(0);	// retrieve both maps in list
		Map<String, Map<String,Double>> observations = result.get(1);
		
		for(String key : observations.keySet()) { // iterate through all keys in observation
			int total = 0; 
			for(String value : observations.get(key).keySet()) {
				total += observations.get(key).get(value); //building the total frequencies for that tag
			}
			
			for(String value : observations.get(key).keySet()) {
				double log = Math.log((observations.get(key).get(value))/total); // compute log
				observations.get(key).put(value, log); //setting to log of the probability of that word given that tag
			}
		}
		for(String key : transitions.keySet()) { // iterate through all keys in transition
			int total = 0;
			for(String value : transitions.get(key).keySet()) { //building total frequencies for that tag
				total += transitions.get(key).get(value);
			}
			
			for(String value : transitions.get(key).keySet()) {
				double log = Math.log((transitions.get(key).get(value))/total); // compute log
				transitions.get(key).put(value, log); //setting to log of the probability of that tag as next state given that tag/current state
			}
		}
		
	}
	
	
	/*
	 * Viterbi algorithm to decode a line
	 * @param line to be decoded
	 * @param data: a list that contains the processed training data - transitions and observations
	 * 
	 */
	public static String viterbi(String line, List<Map<String, Map<String,Double>>> data) {
		Map<String, Map<String,Double>> transitions = data.get(0);	// get the transitions map
		Map<String, Map<String,Double>> observations = data.get(1);	// get the observations map
		
		Map<String, Double> currScores = new HashMap<String, Double>();	// use a map to keep current scores  
		currScores.put("#",0.0); 	//	starts at 0
		line = line.toLowerCase();	// convert to lower case
		String words[] = line.split(" ");	// parse the line
		List<Map<String,String>> backpoint = new ArrayList<Map<String, String>>();	// list of backpointer maps	
		// Loop through each word in the line
		for(int i = 0; i <= words.length-1; i++) {
			Map<String, Double> nextScores = new HashMap<String, Double>();	// create a map containing the next potential states and their scores 
			Map<String, String> backpointers = new HashMap<String, String>(); //create a map of each next state and its previous state
			
			for(String currState : currScores.keySet()) {		// for each current state
				if(transitions.containsKey(currState)) {
					for(String transition : transitions.get(currState).keySet()) {  //get all the possible next states
						double check = -100.0; //unseen value per given by the assignment
						if(observations.containsKey(transition)) {	// if this word is present, change check value
							if(observations.get(transition).containsKey(words[i])) {
								check = observations.get(transition).get(words[i]); //get the log probability of getting this specific next state
							}
						}
						// Compute the next score
						double nextScore = currScores.get(currState) + //path to here
										transitions.get(currState).get(transition) //take a step to get there
										+ check; // make the observation there
						
						// Only keep highest next scores for each state
						if(!nextScores.containsKey(transition) || nextScore > nextScores.get(transition)) { //use nextScores to keep track of nextStates based off of scores
							nextScores.put(transition, nextScore); //adding the score it takes to get to the current word at i
							backpointers.put(transition, currState); //mapping the next state to the current state, or its preceding state
						}
					}
				}
			}
			backpoint.add(backpointers); // add all the backpointers to the list of backpointers
			currScores = nextScores; 	//next scores now become current scores
		}
		
		String output = "";	// build up the path
		String largest = ""; // get the state with the largest value
		for(String state : currScores.keySet()) { //looping through each current state to find the largest final next state (end of the path)
			if(largest.length() == 0) {
				largest = state;
			}
			if(currScores.get(state) > currScores.get(largest)) {
				largest = state; 
			}
		}
		// Back trace back to the start
		output = largest; //add the largest state to the string
		for(int i = backpoint.size()-1; i > 0; i--) { //loop back through the list
			// Set largest to be the state associated with this current state
            largest = backpoint.get(i).get(largest);
			output = largest + " " + output; 	//add it to the output string
		}
		return output; //return the string
	}
	/*
	 * Console input method
	 * Type in sentence, press q to quit
	 */
	public static void console(List<Map<String, Map<String,Double>>> data) {
		System.out.println("Please enter a sentence:");
		Scanner s = new Scanner(System.in);	// create scanner to read inputed sentence
		String input;
		while(true) {
			input = s.nextLine();
			if (input.equals("q")) return;
			System.out.println(viterbi(input, data));	// output viterbi result
		}
	}
	/*
	 * File based test method to measure accuracy of parts of speech tagger
	 * 
	 */
	public static void accuracyChecker(String sentences, String tags, List<Map<String, Map<String,Double>>> data) throws IOException {
		Integer correct = 0;		// track correct and incorrect predictions for tags
		Integer incorrect = 0;
		// reader1 : reads test sentences
		BufferedReader reader1 = new BufferedReader(new FileReader(sentences));
		// reader2 : reads test tags
		BufferedReader reader2 = new BufferedReader(new FileReader(tags));
		
		String sentence;
		String tagLine;
		while((sentence = reader1.readLine()) != null) { 	// loop through every sentence in test sentences
			String output = viterbi(sentence, data);		// obtain viterbi output and split into tags
			String[] outputTags = output.split(" ");	
			
			tagLine = reader2.readLine();	// won't reach error reading because test tags has same # lines as test sentences
			String[] correctTags = tagLine.split(" ");	
			
			for(int i = 0; i < outputTags.length; i++) {		// compare output tag with correct tag 1-by-1
				if(outputTags[i].equals(correctTags[i])) {	// if match, increment correct
					correct++;
				}
				else {		// otherwise, increment incorrect
					incorrect++;
				}
			}
		}
		reader1.close();		// close readers
		reader2.close();
		System.out.println("Number of correct: " + correct);	
		System.out.println("Number of incorrect: " + incorrect);
		// Calculate accuracy percentage
		double accuracy = correct/(double)(incorrect + correct);		
		accuracy = accuracy*100;
		System.out.println("Accuracy: " + accuracy + " %");
	}
	public static void main(String[] args) throws IOException {
		
		System.out.println("Testing viterbi method using simple sentences");
		List<Map<String, Map<String,Double>>> data = training("inputs/simple-test-sentences.txt", "inputs/simple-test-tags.txt");
		
		System.out.println("Testing simple test sentences");
		data = training("inputs/simple-train-sentences.txt", "inputs/simple-train-tags.txt");;
		accuracyChecker("inputs/simple-test-sentences.txt", "inputs/simple-test-tags.txt", data);
		//console(data);
		System.out.println();
		
		System.out.println("Testing brown test sentences");
		data = training("inputs/brown-train-sentences.txt", "inputs/brown-train-tags.txt");
		accuracyChecker("inputs/brown-test-sentences.txt", "inputs/brown-test-tags.txt", data);
		console(data);
		
	}
}
