package edu.isi.bmkeg.sciDT.uima.ae;

import java.io.File;
import java.io.FileReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.DocumentAnnotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.bigmech.fries.FRIES_Argument;
import org.bigmech.fries.FRIES_EntityMention;
import org.bigmech.fries.FRIES_EventMention;
import org.bigmech.fries.FRIES_Frame;
import org.bigmech.fries.FRIES_FrameCollection;
import org.bigmech.fries.FRIES_Passage;
import org.bigmech.fries.FRIES_Position;
import org.bigmech.fries.FRIES_Sentence;
import org.bigmech.fries.FRIES_XRef;
import org.cleartk.ml.feature.extractor.CleartkExtractor;
import org.cleartk.token.type.Sentence;
import org.cleartk.token.type.Token;
import org.simmetrics.StringMetric;
import org.simmetrics.StringMetricBuilder;
import org.simmetrics.metrics.CosineSimilarity;
import org.simmetrics.metrics.Levenshtein;
import org.simmetrics.simplifiers.CaseSimplifier;
import org.simmetrics.simplifiers.NonDiacriticSimplifier;
import org.simmetrics.tokenizers.QGramTokenizer;
import org.simmetrics.tokenizers.WhitespaceTokenizer;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import bioc.type.UimaBioCPassage;
import edu.isi.bmkeg.lapdf.xml.model.LapdftextXMLChunk;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import edu.isi.bmkeg.uimaBioC.utils.NeedlemanWunch;

public class MatchReachAndNxmlText extends JCasAnnotator_ImplBase {

	public final static String PARAM_INPUT_DIRECTORY = ConfigurationParameterFactory
			.createConfigurationParameterName(MatchReachAndNxmlText.class, "inDirPath");
	@ConfigurationParameter(mandatory = true, description = "Directory for the FRIES Frames.")
	String inDirPath;
	File inDir;

	private StringMetric cosineSimilarityMetric;
	private StringMetric levenshteinSimilarityMetric;
	private StringMetric needlemanWunchMetric;
	private NeedlemanWunch nm;
	Pattern alignmentPattern = Pattern.compile("^(.{0,3}_+)");

	private CleartkExtractor<DocumentAnnotation, Token> extractor;

	private static Logger logger = Logger.getLogger(MatchReachAndNxmlText.class);

	private Map<LapdftextXMLChunk, Integer> pgLookup = new HashMap<LapdftextXMLChunk, Integer>();

	private Pattern wbDetect = Pattern.compile("(\\w\\W|\\W\\w)");
	private Pattern wsDetect = Pattern.compile("\\s");

	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		this.inDir = new File(this.inDirPath);

		cosineSimilarityMetric = new StringMetricBuilder().with(new CosineSimilarity<String>())
				.simplify(new CaseSimplifier.Lower()).simplify(new NonDiacriticSimplifier())
				.tokenize(new WhitespaceTokenizer()).tokenize(new QGramTokenizer(2)).build();

		levenshteinSimilarityMetric = new StringMetricBuilder().with(new Levenshtein())
				.simplify(new NonDiacriticSimplifier()).build();

		this.nm = new NeedlemanWunch();
		needlemanWunchMetric = new StringMetricBuilder().with(nm).build();

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {

			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			if (uiD.getId().equals("skip"))
				return;
			
			//UimaBioCUtils.debugSentences(jCas);

			UimaBioCPassage docP = UimaBioCUtils.readDocument(jCas);
			logger.info("Matching " + uiD.getId());

			Map<String, String> infons = UimaBioCUtils.convertInfons(uiD.getInfons());
			String pmcDocId = "PMC" + infons.get("pmc");

			String[] fileTypes = { "json" };
			Collection<File> files = (Collection<File>) FileUtils.listFiles(this.inDir, fileTypes, true);
			File sentenceFrames = null;
			File eventFrames = null;
			File entityFrames = null;
			for (File f : files) {
				if (f.getName().startsWith(pmcDocId + ".uaz.sentences") || 
						f.getName().startsWith(uiD.getId() + ".uaz.sentences") ) {
					sentenceFrames = f;
					break;
				}
			}
			for (File f : files) {
				if (f.getName().startsWith(pmcDocId + ".uaz.events") || 
						f.getName().startsWith(uiD.getId() + ".uaz.events") ) {
					eventFrames = f;
					break;
				}
			}
			for (File f : files) {
				if (f.getName().startsWith(pmcDocId + ".uaz.entities") || 
						f.getName().startsWith(uiD.getId() + ".uaz.entities") ) {
					entityFrames = f;
					break;
				}
			}

			if (eventFrames == null || sentenceFrames == null)
				return;

			final RuntimeTypeAdapterFactory<FRIES_Frame> typeFactory = RuntimeTypeAdapterFactory
					.of(FRIES_Frame.class, "frame-type").registerSubtype(FRIES_EntityMention.class, "entity-mention")
					.registerSubtype(FRIES_Sentence.class, "sentence").registerSubtype(FRIES_Passage.class, "passage")
					.registerSubtype(FRIES_EventMention.class, "event-mention");

			Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
					.registerTypeAdapterFactory(typeFactory).create();

			FRIES_FrameCollection fc1 = gson.fromJson(new FileReader(eventFrames), FRIES_FrameCollection.class);
			Map<String, Set<FRIES_EventMention>> eventMap = new HashMap<String, Set<FRIES_EventMention>>();
			for (FRIES_Frame frame : fc1.getFrames()) {
				if (frame instanceof FRIES_EventMention) {
					FRIES_EventMention em = (FRIES_EventMention) frame;
					String sn = em.getSentence();
					if (!eventMap.containsKey(sn))
						eventMap.put(sn, new HashSet<FRIES_EventMention>());
					eventMap.get(sn).add(em);
				}
			}

			FRIES_FrameCollection fc2 = gson.fromJson(new FileReader(entityFrames), FRIES_FrameCollection.class);

			Map<String, Set<FRIES_EntityMention>> entityMap = new HashMap<String, Set<FRIES_EntityMention>>();
			for (FRIES_Frame frame : fc2.getFrames()) {
				if (frame instanceof FRIES_EntityMention) {
					FRIES_EntityMention em = (FRIES_EntityMention) frame;
					String sn = em.getSentence();
					if (!entityMap.containsKey(sn))
						entityMap.put(sn, new HashSet<FRIES_EntityMention>());
					entityMap.get(sn).add(em);
				}
			}
			
			FRIES_FrameCollection fc3 = gson.fromJson(new FileReader(sentenceFrames), FRIES_FrameCollection.class);
			List<UimaBioCAnnotation> passages = UimaBioCUtils.readAllReadablePassagesAndFloats(jCas);
			passages.addAll( UimaBioCUtils.readFloats(jCas) );
			
			Map<String, FRIES_Passage> passageMap = new HashMap<String, FRIES_Passage>();
			for (FRIES_Frame frame : fc3.getFrames()) {
				if (frame instanceof FRIES_Passage) {
					FRIES_Passage psg = (FRIES_Passage) frame;
					passageMap.put(frame.getFrameId(), psg);
				}
			}
			
			FRIES_FRAMES: for (FRIES_Frame frame : fc3.getFrames()) {

				if (frame instanceof FRIES_Sentence) {

					FRIES_Sentence fs = (FRIES_Sentence) frame;

					String passageId = fs.getPassage();
					FRIES_Passage psg = passageMap.get(passageId);
					
					if ( !eventMap.containsKey(fs.getFrameId()) &&
							!entityMap.containsKey(fs.getFrameId()))
						continue;

					if (fs.getText().length() < 10)
						continue;

					float bestSim = 0.0f;
					UimaBioCAnnotation match = null;
					for (UimaBioCAnnotation p : passages) {
						float sim = cosineSimilarityMetric.compare(p.getCoveredText(), psg.getText());
						if (sim > bestSim) {
							match = p;
							bestSim = sim;
						}
					}

					if (match != null) {
						
						FRIES_Position startPos = fs.getStartPos();
						int s = startPos.getOffset();
						FRIES_Position endPos = fs.getEndPos();
						int e = endPos.getOffset();
						String sentTxt = psg.getText().substring(s, e);
						String ss1 = fs.getText().replaceAll("\\s+", "");
						
						//
						// Find the sentence that starts and ends at the closest point 
						// to the current sentence. 
						// 
						// - Note that there are errors in both FRIES 
						//   and our sentence splitting
						//
						int[] delts = {10000, 10000};
						Sentence matchingSentence = null;
						for (Sentence ss : JCasUtil.selectCovered(Sentence.class, match) ) {
							
							String ss2 = UimaBioCUtils.friesifySentence(jCas, ss);
							ss2 = ss2.replaceAll("\\s+", "");

							if( ss1.equals(ss2) ) {
							
								matchingSentence = ss;
								break;
							
							} else {
								
								int l = Math.min(ss1.length(), ss2.length());
								float sim1 = levenshteinSimilarityMetric.compare(
										ss1.substring(0,l), ss2.substring(0,l));
								float sim2 = levenshteinSimilarityMetric.compare(
										ss1.substring(ss1.length()-l, ss1.length()),
										ss2.substring(ss2.length()-l, ss2.length()));
								
								if ( sim1 > 0.8 ||
										sim2 > 0.8 ) {
									matchingSentence = ss;
									
									if ( sim1 < 0.8 || sim2 < 0.8 ) 
										if(ss1.length()<ss2.length())
											logger.warn("Likely error in ClearTk sentence splitting for '" 
													+ matchingSentence.getCoveredText() + "'");
										else 
											logger.warn("Likely error in REACH sentence splitting for " + fs.getFrameId() + ": " + fs.getText());	
									
									break;
								}
								
							}
							
							/*if( (ss.getEnd() - ss.getBegin()) < 10 )
								continue;
									
							int d0 = Math.abs((ss.getBegin() - match.getBegin()) - s); 
							int d1 = Math.abs((ss.getEnd() - match.getBegin()) - e); 
							
							if( d0 == 0 && d1 == 0 ) {
								delts[0] = d0;
								delts[1] = d1;
								matchingSentence = ss;
								break;
							} else if( d0 < delts[0] && d1 < delts[1]  ) {
								delts[0] = d0;
								delts[1] = d1;
								matchingSentence = ss;
							}*/

						}

						//
						// Check for the best match based on Levenshtein edit distance
						// to make sure that it's correct. We also truncate the match 
						// to the shorter of the two sentences to avoid errors 
						// from sentence splitting.
						//
						if( matchingSentence == null ) {
							logger.warn("A. Can't find a match for " + fs.getFrameId() + ": " + fs.getText());
							continue FRIES_FRAMES;
						}
						
						if (eventMap.containsKey(fs.getFrameId())) {
							for (FRIES_EventMention em : eventMap.get(fs.getFrameId())) {
								matchFriesEventToClearTkSentence(jCas, bestSim, matchingSentence, em);
							}
						} 
						if (entityMap.containsKey(fs.getFrameId())) {
							for (FRIES_EntityMention em : entityMap.get(fs.getFrameId())) {
								matchFriesEventToClearTkSentence(jCas, bestSim, matchingSentence, em);
							}
						}
						
					} else {
						
						logger.warn("C. Can't find a match for " + fs.getFrameId() + ": " + fs.getText());
						// Need to track when we can't find these things. 
						
					}int i=0;
					
				}
				
			}
			
			logger.info("Matching " + uiD.getId() + " - COMPLETED");

		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}

	private void matchFriesEventToClearTkSentence(JCas jCas, float bestSim, Sentence match, FRIES_Frame f) {
		
		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		
		UimaBioCAnnotation a = new UimaBioCAnnotation(jCas);
		Map<String, String> inf = new HashMap<String, String>();
		inf.put("eventId", f.getFrameId());
		inf.put("score", bestSim + "");
		
		if( f instanceof FRIES_EventMention) {
			
			FRIES_EventMention em = (FRIES_EventMention) f;
			inf.put("passId", em.getSentence());
			inf.put("sentId", em.getSentence());
			inf.put("type", "FRIES_EventMention");
			inf.put("fType", em.getType());
			inf.put("fSubType", em.getSubtype());
			inf.put("friesText", em.getText());
			String code = "";
			for (FRIES_Argument args : em.getArguments()) {
				code += "[" + args.getText() + "]";
			}
			inf.put("value", code);
			a.setInfons(UimaBioCUtils.convertInfons(inf, jCas));
			
		} else  if( f instanceof FRIES_EntityMention) {
			
			FRIES_EntityMention em = (FRIES_EntityMention) f;
			inf.put("sentId", em.getSentence());
			inf.put("type", "FRIES_EntityMention");
			inf.put("fType", em.getType());
			inf.put("fSubType", em.getSubtype());
			inf.put("friesText", em.getText());
			String code = "";
			for (FRIES_XRef args : em.getXrefs()) {
				code += "[" + args.getNamespace() + ":" + args.getId() + "]";
			}
			inf.put("value", code);
			a.setInfons(UimaBioCUtils.convertInfons(inf, jCas));
			
		}

		
		// Need to align the text from FRIES
		// Ideally find an exact match
		int pos = match.getCoveredText().lastIndexOf(f.getText());
		if (pos != -1) {

			a.setBegin(match.getBegin() + pos);
			a.setEnd(match.getBegin() + pos + f.getText().length());
			a.addToIndexes(jCas);

		} else {

			//
			// If that doesn't work then match based on
			// from incorrectly matched whitespace.
			//
			String no_ws1 = match.getCoveredText().replaceAll("\\s+", "");
			String no_ws2 = f.getText().replaceAll("\\s+", "");
			int no_ws_pos = no_ws1.lastIndexOf(no_ws2) + 1;

			if (no_ws_pos != -1) {
				String s1 = match.getCoveredText();
				int startPos = 0;
				int endPos = 0;
				int p = 0;
				for (int i = 0; i < s1.length(); i++) {
					if (!s1.substring(i, i + 1).matches("\\s"))
						p++;
					if (p == no_ws_pos)
						startPos = i;
					if (p == no_ws_pos + no_ws2.length())
						endPos = i;
				}
				if( endPos == 0 ) 
					endPos = match.getEnd();
				
				a.setBegin(match.getBegin() + startPos);
				a.setEnd(match.getBegin() + endPos);
				try {
					logger.debug(uiD.getId() + " [whitespace] ClearTk: " + a.getCoveredText());
					logger.debug(" [whitespace] FRIES: " + f.getText());
					a.addToIndexes(jCas);
				} catch( Exception e ) {
					e.printStackTrace();
				}
			}
			//
			// If that doesn't work,
			// use Needleman Wunch algorithm.
			//
			else {

				float sim2 = this.needlemanWunchMetric.compare(match.getCoveredText(),
						f.getText());
				String s1Align = this.nm.getString1Alignment();
				String s2Align = this.nm.getString2Alignment();

				//
				// Using this algorithm, the string
				// alignments seems
				// to make a substitution error at the
				// start, which
				// looks like "f__________"
				// - We detect this and correct for it.
				//
				Matcher m = this.alignmentPattern.matcher(s2Align);
				if (m.find()) {
					int offset = m.group(1).length() - 1;

					String s1Remain = s1Align.substring(offset, s1Align.length());
					int s1ModCount = this.countChars(s1Remain, "_");

					String s2Remain = s2Align.substring(offset, s2Align.length());
					int s2ModCount = this.countChars(s2Remain, "_");

					int len = s2Remain.length() - s2ModCount + s1ModCount;

					a.setBegin(match.getBegin() + offset);
					a.setEnd(match.getBegin() + offset + len);
					try {
						logger.debug(uiD.getId() + " [align] ClearTk: " + a.getCoveredText());
						logger.debug(" [align] FRIES: " + f.getText());
						a.addToIndexes(jCas);
					} catch( Exception e ) {
						e.printStackTrace();
					}

				} else {

					a.setBegin(match.getBegin());
					a.setEnd(match.getEnd());
					try {
						logger.warn("Entity: " + f.getText());
						logger.warn("Error Aligning Texts");
						a.addToIndexes(jCas);
					} catch( Exception e ) {
						e.printStackTrace();
					}

				}
				
			}
			
		}
		
	}

	private int countChars(String s, String c) {

		String mod = s.replaceAll(c, "");
		return (s.length() - mod.length());

	}
	
	
	

}
