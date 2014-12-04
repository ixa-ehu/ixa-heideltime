package ixa.pipe.time;

import ixa.kaflib.KAFDocument;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.XMLInputSource;
import org.apache.uima.util.XMLSerializer;

import de.unihd.dbs.heideltime.standalone.CLISwitch;
import de.unihd.dbs.heideltime.standalone.Config;
import de.unihd.dbs.heideltime.standalone.DocumentType;
import de.unihd.dbs.heideltime.standalone.HeidelTimeStandalone;
import de.unihd.dbs.heideltime.standalone.OutputType;
import de.unihd.dbs.heideltime.standalone.POSTagger;
import de.unihd.dbs.heideltime.standalone.components.JCasFactory;
import de.unihd.dbs.heideltime.standalone.components.PartOfSpeechTagger;
import de.unihd.dbs.heideltime.standalone.components.ResultFormatter;
import de.unihd.dbs.heideltime.standalone.components.impl.IntervalTaggerWrapper;
import de.unihd.dbs.heideltime.standalone.components.impl.JCasFactoryImpl;
import de.unihd.dbs.heideltime.standalone.components.impl.JVnTextProWrapper;
import de.unihd.dbs.heideltime.standalone.components.impl.StanfordPOSTaggerWrapper;
import de.unihd.dbs.heideltime.standalone.components.impl.TimeMLResultFormatter;
import de.unihd.dbs.heideltime.standalone.components.impl.TreeTaggerWrapper;
import de.unihd.dbs.heideltime.standalone.components.impl.UimaContextImpl;
import de.unihd.dbs.heideltime.standalone.components.impl.XMIResultFormatter;
import de.unihd.dbs.heideltime.standalone.exceptions.DocumentCreationTimeMissingException;
import de.unihd.dbs.uima.annotator.heideltime.HeidelTime;
import de.unihd.dbs.uima.annotator.heideltime.resources.Language;
import de.unihd.dbs.uima.annotator.intervaltagger.IntervalTagger;
import de.unihd.dbs.uima.types.heideltime.Dct;
import de.unihd.dbs.uima.types.heideltime.Timex3;
import de.unihd.dbs.uima.types.heideltime.Timex3Interval;

public class IXAPipeHeidelTime{
	/**
	 * Used document type
	 */
	private DocumentType documentType;

	/**
	 * HeidelTime instance
	 */
	private HeidelTime heidelTime;

	/**
	 * Type system description of HeidelTime
	 */
	private JCasFactory jcasFactory;

	/**
	 * Used language
	 */
	private Language language;

	/**
	 * output format
	 */
	private OutputType outputType;

	/**
	 * POS tagger
	 */
	private POSTagger posTagger;

	/**
	 * Whether or not to do Interval Tagging
	 */
	private Boolean doIntervalTagging;
	
	private String mappingFile;

	/**
	 * Logging engine
	 */
	private static Logger logger = Logger.getLogger("IXAPipeHeidelTime");

	
	/**
	 * empty constructor.
	 * 
	 * call initialize() after using this!
	 * 
	 * @param language
	 * @param typeToProcess
	 * @param outputType
	 */
	public IXAPipeHeidelTime() {
	}
	
	/** Constructor with KAFDocument. Used only for ixa-pipes purposes 
	 * 
	 */
	public IXAPipeHeidelTime(String lang,String mappingFile,String configPath){
		if (lang.equals("es")){
			this.language = Language.SPANISH;
		}
		else if (lang.equals("nl")){
			this.language = Language.DUTCH;
		}
		else{
			
		}
		this.documentType = DocumentType.NEWS;
		this.outputType = OutputType.TIMEML;
		
		this.mappingFile = mappingFile;
		
		// set doIntervalTagging flag
		this.doIntervalTagging = true;
		
		initialize(this.language, this.documentType,configPath);
	}
	

	/**
	 * Method that initializes all vital prerequisites, including POS Tagger
	 * 
	 * @param language	Language to be processed with this copy of HeidelTime
	 * @param typeToProcess	Domain type to be processed => set to NEWS
	 * 
	 */
	public void initialize(Language language, DocumentType typeToProcess, String configPath){
		logger.log(Level.INFO, "IXAPipeHeidelTime initialized with language " + this.language.getName());

		// read in configuration in case it's not yet initialized
		if(!Config.isInitialized()) {
			if(configPath == null)
				readConfigFile(CLISwitch.CONFIGFILE.getValue().toString());
			else
				readConfigFile(configPath);
		}
		
		try {
			heidelTime = new HeidelTime();
			heidelTime.initialize(new UimaContextImpl(language, typeToProcess));
			logger.log(Level.INFO, "HeidelTime initialized");
		} catch (Exception e) {
			e.printStackTrace();
			logger.log(Level.WARNING, "HeidelTime could not be initialized");
		}

		// Initialize JCas factory -------------
		logger.log(Level.FINE, "Initializing JCas factory...");
		try {
			TypeSystemDescription[] descriptions = new TypeSystemDescription[] {
					UIMAFramework
							.getXMLParser()
							.parseTypeSystemDescription(
									new XMLInputSource(
											this.getClass()
													.getClassLoader()
													.getResource(
															Config.get(Config.TYPESYSTEMHOME)))),
					UIMAFramework
							.getXMLParser()
							.parseTypeSystemDescription(
									new XMLInputSource(
											this.getClass()
													.getClassLoader()
													.getResource(
															Config.get(Config.TYPESYSTEMHOME_DKPRO)))) };
			jcasFactory = new JCasFactoryImpl(descriptions);
			logger.log(Level.INFO, "JCas factory initialized");
		} catch (Exception e) {
			e.printStackTrace();
			logger.log(Level.WARNING, "JCas factory could not be initialized");
		}
	}
	
	/**
	 * Runs the IntervalTagger on the JCAS object.
	 * @param jcas jcas object
	 */
	private void runIntervalTagger(JCas jcas) {
		logger.log(Level.FINEST, "Running Interval Tagger...");
		Integer beforeAnnotations = jcas.getAnnotationIndex().size();
		
		// Prepare the options for IntervalTagger's execution
		Properties settings = new Properties();
		settings.put(IntervalTagger.PARAM_LANGUAGE, language.getResourceFolder());
		settings.put(IntervalTagger.PARAM_INTERVALS, true);
		settings.put(IntervalTagger.PARAM_INTERVAL_CANDIDATES, false);
		
		// Instantiate and process with IntervalTagger
		IntervalTaggerWrapper iTagger = new IntervalTaggerWrapper();
		iTagger.initialize(settings);
		iTagger.process(jcas);
		
		// debug output
		Integer afterAnnotations = jcas.getAnnotationIndex().size();
		logger.log(Level.FINEST, "Annotation delta: " + (afterAnnotations - beforeAnnotations));
	}

	/**
	 * Provides jcas object with document creation time if
	 * <code>documentCreationTime</code> is not null.
	 * 
	 * @param jcas
	 * @param documentCreationTime
	 * @throws DocumentCreationTimeMissingException
	 *             If document creation time is missing when processing a
	 *             document of type {@link DocumentType#NEWS}.
	 */
	private void provideDocumentCreationTime(JCas jcas,
			Date documentCreationTime)
			throws DocumentCreationTimeMissingException {
		if (documentCreationTime == null) {
			// Document creation time is missing
			if (documentType == DocumentType.NEWS) {
				// But should be provided in case of news-document
				throw new DocumentCreationTimeMissingException();
			}
			if (documentType == DocumentType.COLLOQUIAL) {
				// But should be provided in case of colloquial-document
				throw new DocumentCreationTimeMissingException();
			}
		} else {
			// Document creation time provided
			// Translate it to expected string format
			SimpleDateFormat dateFormatter = new SimpleDateFormat(
					"yyyy.MM.dd'T'HH:mm");
			String formattedDCT = dateFormatter.format(documentCreationTime);

			// Create dct object for jcas
			Dct dct = new Dct(jcas);
			dct.setValue(formattedDCT);

			dct.addToIndexes();
		}
	}


	private void establishHeidelTimePreconditions(JCas jcas, NAFWrapper wrapper) {
		// Token information & sentence structure
		logger.log(Level.FINEST, "Establishing part of speech information...");
		try {
			wrapper.process(jcas);
		} catch (AnalysisEngineProcessException e) {
			e.printStackTrace();
		}
		logger.log(Level.FINEST, "Part of speech information established");
	}

	@SuppressWarnings("unused")
	private ResultFormatter getFormatter() {
		if (outputType.toString().equals("xmi")){
			return new XMIResultFormatter();
		} else {
			return new TimeMLResultFormatter();
		}
	}
	
	

	/**
	 * Processes document with HeidelTime
	 * 
	 * @param document
	 * @param documentCreationTime
	 *            Date when document was created - especially important if
	 *            document is of type {@link DocumentType#NEWS}
	 * @return Annotated document
	 * @throws DocumentCreationTimeMissingException
	 *             If document creation time is missing when processing a
	 *             document of type {@link DocumentType#NEWS}
	 */
	public void process (KAFDocument kaf) throws DocumentCreationTimeMissingException {
		logger.log(Level.INFO, "Processing started");

		NAFWrapper wrapper = new NAFWrapper(kaf,mappingFile);
		
		// Generate jcas object ----------
		logger.log(Level.FINE, "Generate CAS object");
		JCas jcas = null;
		try {
			jcas = jcasFactory.createJCas();
			String document = wrapper.getText();
			jcas.setDocumentText(document);
			logger.log(Level.FINE, "CAS object generated");
		} catch (Exception e) {
			e.printStackTrace();
			logger.log(Level.WARNING, "Cas object could not be generated");
		}

		// Process jcas object -----------
		try {
			logger.log(Level.FINER, "Establishing preconditions...");
			Date documentCreationTime = wrapper.creationTime();
			provideDocumentCreationTime(jcas, documentCreationTime);
			establishHeidelTimePreconditions(jcas,wrapper);
			logger.log(Level.FINER, "Preconditions established");

			heidelTime.process(jcas);

			logger.log(Level.INFO, "Processing finished");
		} catch (Exception e) {
			e.printStackTrace();
			logger.log(Level.WARNING, "Processing aborted due to errors");
		}

		// process interval tagging ---
		if(doIntervalTagging)
			runIntervalTagger(jcas);
		
		// Process results ---------------
		logger.log(Level.FINE, "Formatting result...");

		try {
			format(wrapper,jcas);
			
			logger.log(Level.INFO, "Result formatted");
		} catch (Exception e) {
			e.printStackTrace();
			logger.log(Level.WARNING, "Result could not be formatted");
		}

	}
	
	private void format (NAFWrapper wrapper, JCas jcas){
		/* 
		 * loop through the timexes to create two treemaps:
		 * - one containing startingposition=>timex tuples for eradication of overlapping timexes
		 * - one containing endposition=>timex tuples for assembly of the XML file
		 */
		FSIterator iterTimex = jcas.getAnnotationIndex(Timex3.type).iterator();
		TreeMap<Integer, Timex3> forwardTimexes = new TreeMap<Integer, Timex3>();
		while(iterTimex.hasNext()) {
			Timex3 t = (Timex3) iterTimex.next();
			forwardTimexes.put(t.getBegin(), t);
		}
		
		HashSet<Timex3> timexesToSkip = new HashSet<Timex3>();
		Timex3 prevT = null;
		Timex3 thisT = null;
		// iterate over timexes to find overlaps
		for(Integer begin : forwardTimexes.navigableKeySet()) {
			thisT = (Timex3) forwardTimexes.get(begin);
			
			// check for whether this and the previous timex overlap. ex: [early (friday] morning)
			if(prevT != null && prevT.getEnd() > thisT.getBegin()) {
				
				Timex3 removedT = null; // only for debug message
				// assuming longer value string means better granularity
				if(prevT.getTimexValue().length() > thisT.getTimexValue().length()) {
					timexesToSkip.add(thisT);
					removedT = thisT;
					/* prevT stays the same. */
				} else {
					timexesToSkip.add(prevT);
					removedT = prevT;
					prevT = thisT; // this iteration's prevT was removed; setting for new iteration 
				}
				
				// ask user to let us know about possibly incomplete rules
				Logger l = Logger.getLogger("TimeMLResultFormatter");
				l.log(Level.WARNING, "Two overlapping Timexes have been discovered:" + System.getProperty("line.separator")
						+ "Timex A: " + prevT.getCoveredText() + " [\"" + prevT.getTimexValue() + "\" / " + prevT.getBegin() + ":" + prevT.getEnd() + "]" 
						+ System.getProperty("line.separator")
						+ "Timex B: " + removedT.getCoveredText() + " [\"" + removedT.getTimexValue() + "\" / " + removedT.getBegin() + ":" + removedT.getEnd() + "]" 
						+ " [removed]" + System.getProperty("line.separator")
						+ "The writer chose, for granularity: " + prevT.getCoveredText() + System.getProperty("line.separator")
						+ "This usually happens with an incomplete ruleset. Please consider adding "
						+ "a new rule that covers the entire expression.");
			} else { // no overlap found? set current timex as next iteration's previous timex
				prevT = thisT;
			}
		}
		
		
		for(Integer begin : forwardTimexes.navigableKeySet()) {
			Timex3 t = (Timex3) forwardTimexes.get(begin);
			if (!timexesToSkip.contains(t)){
				int beginOffset = t.getBegin();
				int endOffset = t.getEnd();
				String value = t.getTimexValue();
				String type = t.getTimexType();
				int sentence = t.getSentId();
				wrapper.addTimex(sentence,beginOffset, endOffset, value, type);
			}
		}
		
	}
	
	
	public static void readConfigFile(String configPath) {
		InputStream configStream = null;
		try {
			logger.log(Level.INFO, "trying to read in file "+configPath);
			configStream = new FileInputStream(configPath);
			
			Properties props = new Properties();
			props.load(configStream);

			Config.setProps(props);
			
			configStream.close();
		} catch (FileNotFoundException e) {
			logger.log(Level.WARNING, "couldn't open configuration file \""+configPath+"\". quitting.");
			System.exit(-1);
		} catch (IOException e) {
			logger.log(Level.WARNING, "couldn't close config file handle");
			e.printStackTrace();
		}
	}
	
	private static void printHelp() {
		String path = HeidelTimeStandalone.class.getProtectionDomain().getCodeSource().getLocation().getFile();
		String filename = path.substring(path.lastIndexOf(System.getProperty("file.separator")) + 1);
		
		System.out.println("Usage:");
		System.out.println("  java -jar " 
				+ filename 
				+ " <input-document> [-param1 <value1> ...]");
		System.out.println();
		System.out.println("Parameters and expected values:");
		for(CLISwitch c : CLISwitch.values()) {
			System.out.println("  " 
					+ c.getSwitchString() 
					+ "\t"
					+ ((c.getSwitchString().length() > 4)? "" : "\t")
					+ c.getName()
					);

			if(c == CLISwitch.LANGUAGE) {
				System.out.print("\t\t" + "Available languages: [ ");
				for(Language l : Language.values())
					if(l != Language.WILDCARD)
						System.out.print(l.getName().toLowerCase()+" ");
				System.out.println("]");
			}
			
			if(c == CLISwitch.POSTAGGER) {
				System.out.print("\t\t" + "Available taggers: [ ");
				for(POSTagger p : POSTagger.values())
					System.out.print(p.toString().toLowerCase()+" ");
				System.out.println("]");
			}
			
			if(c == CLISwitch.DOCTYPE) {
				System.out.print("\t\t" + "Available types: [ ");
				for(DocumentType t : DocumentType.values())
					System.out.print(t.toString().toLowerCase()+" ");
				System.out.println("]");
			}
		}
		
		System.out.println();
	}

	public DocumentType getDocumentType() {
		return documentType;
	}

	public void setDocumentType(DocumentType documentType) {
		this.documentType = documentType;
	}

	public Language getLanguage() {
		return language;
	}

	public void setLanguage(Language language) {
		this.language = language;
	}

	public OutputType getOutputType() {
		return outputType;
	}

	public void setOutputType(OutputType outputType) {
		this.outputType = outputType;
	}

	public final POSTagger getPosTagger() {
		return posTagger;
	}

	public final void setPosTagger(POSTagger posTagger) {
		this.posTagger = posTagger;
	}


}
