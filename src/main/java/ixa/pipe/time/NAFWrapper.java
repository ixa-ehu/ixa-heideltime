package ixa.pipe.time;


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;

import de.unihd.dbs.uima.annotator.heideltime.utilities.Logger;
import de.unihd.dbs.uima.types.heideltime.Sentence;
import de.unihd.dbs.uima.types.heideltime.Token;
import ixa.kaflib.KAFDocument;
import ixa.kaflib.Term;
import ixa.kaflib.WF;

public class NAFWrapper{
	private KAFDocument kaf;
	private HashMap<String,String> mapping;
	private HashMap<String,HashSet<String>> exceptions;
	final String DELIMITER = "\t";
	
	NAFWrapper(KAFDocument kaf,String file){
		this.kaf = kaf;
		initMapping(file);
	}
	

	private void initMapping(String file){
		mapping = new HashMap<String,String>();
		exceptions = new HashMap<String, HashSet<String>>();
		
		BufferedReader br = null;
		Pattern lemmaPattern = Pattern.compile("Lemma\\s(.*)$");
		Pattern exceptPattern = Pattern.compile("except\\slemma\\:\\s(.*)$");
		Pattern includePattern = Pattern.compile("lemma\\:\\s(.*)$");
		try {
			String line = "";
			br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));			
			//Read the file line by line
			while ((line = br.readLine()) != null) {
				String[] tags = line.split(DELIMITER);
				String ixaPipes = tags[0];
				String treetagger = tags[1];
				mapping.put(ixaPipes, treetagger);
				if (ixaPipes.matches("Lemma.*")){
					HashSet<String> info = new HashSet<String>();
					Matcher matcher = lemmaPattern.matcher(ixaPipes);
					while (matcher.find()){
						info.add(matcher.group(1));
					}
					exceptions.put(ixaPipes, info);
				}
				else if (ixaPipes.matches("except lemma.*")){
					HashSet<String> info = new HashSet<String>();
					Matcher matcher = exceptPattern.matcher(ixaPipes);
					while (matcher.find()){
						String foundLemmas = matcher.group(1);
						String[] fLemmas = foundLemmas.split(",");
						for (String f: fLemmas){
							info.add(f);
						}
					}
					exceptions.put(ixaPipes, info);
				}
				else if (ixaPipes.matches("lemma.*")){
					HashSet<String> info = new HashSet<String>();
					Matcher matcher = includePattern.matcher(ixaPipes);
					while (matcher.find()){
						String foundLemmas = matcher.group(1);
						String[] fLemmas = foundLemmas.split(",");
						for (String f: fLemmas){
							info.add(f);
						}
					}
					exceptions.put(ixaPipes, info);
				}
			}
 
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null)br.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
 
	}
	
	private String getPOS (Term term){
		String pos = "";
		String morpho = term.getMorphofeat();
		String lemma = term.getLemma();
		for (String toCompare:this.mapping.keySet()){
			if (toCompare.matches("Lemma.*")){
				HashSet<String> lemmas = exceptions.get(toCompare);
				if (lemmas.contains(lemma)){
					return mapping.get(toCompare);
				}
			}
			else if (toCompare.matches("except lemma.*")){
				HashSet<String> lemmas = exceptions.get(toCompare);
				if (!lemmas.contains(lemma)){
					return mapping.get(toCompare);
				}
			}
			else if (toCompare.matches("lemma.*")){
				HashSet<String> lemmas = exceptions.get(toCompare);
				if (lemmas.contains(lemma)){
					return mapping.get(toCompare);
				}
			}
			else if (morpho.matches(toCompare)){
				return mapping.get(toCompare);
			}
		}
		
		return pos;
		
	}
	
	public String language(){
		return kaf.getLang();
	}
	
	@SuppressWarnings("finally")
	public Date creationTime(){
		//SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-ddThh:mm:ssz");
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		
		//assign back-up value to date (will be overwritten by dct)
		//kafnaflib can throw exception when no dct is given
	    Calendar cal = Calendar.getInstance();
		Date date = cal.getTime();
		try {
		    String dct = kaf.getFileDesc().creationtime;
		    if ( dct != null){
			date = format.parse(dct);
			cal.setTime(date);
		    }
		    else{
			date = cal.getTime();
		    }
		    ixa.kaflib.Timex3 time = kaf.newTimex3("DATE");
		    String dctToString = getTimexFormat(cal);
		    time.setValue(dctToString);
		    time.setFunctionInDocument("CREATION_TIME");

		} catch (ParseException e) {
			e.printStackTrace();
		}
		finally {
			return date;
		}
		
	}
	
	public String getText(){
		String text = "";
		int textOffset = 0;
		List<WF> wordForms = kaf.getWFs();
		for (int i = 0; i < wordForms.size(); i++) {
		    WF wordForm = wordForms.get(i);
		    if (textOffset != wordForm.getOffset()){
			while(textOffset < wordForm.getOffset()) {
			    text += " ";
			    textOffset += 1;
			}
		    }
		    text += wordForm.getForm();
		    textOffset += wordForm.getLength();
		}
		return text;
	}
	
	/**
	 * Method that gets called to process the documents' jcas objects
	 */
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		Integer offset = 0; // a cursor of sorts to keep up with the position in the document text
		
		// grab the document text
		String docText = jcas.getDocumentText();
		
		// iterate over sentences in this document
		int pos = kaf.getFirstSentence();
		int last = kaf.getNumSentences();
		for (int i = pos; i <= last; i++){
			
			// create a sentence object. gets added to index or discarded depending on configuration
			Sentence sentence = new Sentence(jcas);
			sentence.setSentenceId(i);
			sentence.setBegin(offset);
			
			Integer wordCount = 0;
			// iterate over words in this sentence
			
			List<Term> terms = kaf.getSentenceTerms(i);
			for (Term term: terms){
				
				Token t = new Token(jcas);
				t.setPos(getPOS(term));
				
				String thisWord = term.getForm();
				if(docText.indexOf(thisWord, offset) < 0) {
					Logger.printDetail("A previously tagged token wasn't found in the document text: \"" + thisWord + "\". " +
							"This may be due to unpredictable punctuation tokenization; hence this token isn't tagged.");
					continue; // jump to next token: discards token
				} else {
					offset = docText.indexOf(thisWord, offset); // set cursor to the starting position of token in docText
					t.setBegin(offset);
					++wordCount;
				}
				
				offset += thisWord.length(); // move cursor behind the word
				t.setEnd(offset);
				
				t.addToIndexes();
				
			}
			
			if(wordCount == 0)
				sentence.setEnd(offset);
			else
				sentence.setEnd(offset-1);
			sentence.addToIndexes();
			
		}
		
		// TODO: DEBUG
		FSIterator fsi = jcas.getAnnotationIndex(Sentence.type).iterator();
		while(fsi.hasNext()) {
			Sentence s = (Sentence) fsi.next();
			if(s.getBegin() < 0 || s.getEnd() < 0) {
				System.err.println("Sentence: " + s.getBegin() + ":" + s.getEnd() + " = " + s.getCoveredText());
				System.err.println("wrong index in text: " + jcas.getDocumentText());
				System.exit(-1);
			}
		}
		FSIterator fsi2 = jcas.getAnnotationIndex(Token.type).iterator();
		while(fsi2.hasNext()) {
			Token t = (Token) fsi2.next();
			if(t.getBegin() < 0 || t.getEnd() < 0) {
				System.err.println("In text: " + jcas.getDocumentText());
				System.err.println("Token: " + t.getBegin() + ":" + t.getEnd());
				System.exit(-1);
			}
		}
	}
	
	public void addTimex(int sentence,int begin, int end, String value, String type){
		ixa.kaflib.Timex3 time = kaf.newTimex3(type);
		time.setValue(value);
		
		List<WF> wfs = kaf.getWFsBySent(sentence);
		List<WF> wfSpan = new ArrayList<WF>();
		
		for (WF wf:wfs){
			int offset = wf.getOffset();
			if (offset >= begin && offset < end){
				wfSpan.add(wf);
			}
		}
		time.setSpan(KAFDocument.newWFSpan(wfSpan));
	}
	
    private String getTimexFormat(Calendar cal){
	String format = null;
	
	int year = cal.get(Calendar.YEAR);
	format = Integer.toString(year) + "-";
	int month = cal.get(Calendar.MONTH) + 1;
	if (month < 10){
	    format += Integer.toString(0);
	}
	format += Integer.toString(month) + "-";
	int day = cal.get(Calendar.DAY_OF_MONTH);
	if (day < 10){
	    format += Integer.toString(0);
	}
	format += Integer.toString(day);
	

	return format;
    }

}
