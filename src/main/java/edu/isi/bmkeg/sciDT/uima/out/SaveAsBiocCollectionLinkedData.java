package edu.isi.bmkeg.sciDT.uima.out;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.token.type.Sentence;
import org.cleartk.token.type.Token;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import bioc.BioCCollection;
import bioc.BioCDocument;
import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import edu.isi.bmkeg.uimaBioC.BioCLinkedDataUtils;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;
import edu.isi.bmkeg.utils.MapCreate;

public class SaveAsBiocCollectionLinkedData extends JCasAnnotator_ImplBase {

	public final static String PARAM_TEMP_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveAsBiocCollectionLinkedData.class, "tempDirPath");
	@ConfigurationParameter(mandatory = true, description = "Temporary place to put files.")
	String tempDirPath;

	public final static String PARAM_FILE_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveAsBiocCollectionLinkedData.class, "outFilePath");
	@ConfigurationParameter(mandatory = true, description = "Output filename.")
	String outFilePath;

	/*
	 * public final static String PARAM_JSONLD_CONTEXT_PATH =
	 * ConfigurationParameterFactory
	 * .createConfigurationParameterName(SaveAsBiocCollectionLinkedData.class,
	 * "jsonldContextPath");
	 * 
	 * @ConfigurationParameter(mandatory = true, description =
	 * "File that describes the context for external data contained in infons relations."
	 * ) String jsonldContextPath;
	 */

	private File tempDir;
	private File outFile;
	// private File jsonLdContextFile;
	private BioCCollection collection;

	private Pattern figPatt;
	private List<Pattern> subFigPatt = new ArrayList<Pattern>();
	private OntModel ontModel;
	
	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		this.tempDirPath = (String) context.getConfigParameterValue(PARAM_TEMP_PATH);
		this.tempDir = new File(this.tempDirPath);

		if(!this.tempDir.exists())
			this.tempDir.mkdirs();
		
		this.outFilePath = (String) context.getConfigParameterValue(PARAM_FILE_PATH);
		this.outFile = new File(this.outFilePath);

		/*
		 * this.jsonldContextPath = (String) context
		 * .getConfigParameterValue(PARAM_JSONLD_CONTEXT_PATH);
		 * this.jsonLdContextFile = new File(this.jsonldContextPath);
		 */

		this.figPatt = Pattern.compile("\\s*[Ff]ig(ure|.){0,1}\\s+(\\d+)");
		this.subFigPatt.add(Pattern.compile("^\\s*\\(\\s*([A-Za-z])\\s*\\)"));
		
	}

	/**
	 * The eventual goal is to go through each element of the BioC document and
	 * render them as BioC-LD. If we do not encounter any annotations defined in
	 * the context file, then we should drop the annotation. Thus the connection
	 * between the BioC standard and BioC-LD is the context file.
	 * 
	 * Here we need to get this to work now, so we hard-code the solution for
	 * sciDT data.
	 * 
	 * This should provide a workable conversion between BioC and BioC-LD. If
	 * you want to convert your 'standard' bioc data to bioc-ld, the infons need
	 * to be formatted in the context file.
	 */
	public void process(JCas jCas) throws AnalysisEngineProcessException {

		try {

			UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
			if (uiD.getId().equals("skip"))
				return;

			//
			// Map figure codes to clause-level annotations to allow us to build
			// annotations across clauses.
			//
			List<UimaBioCAnnotation> annotations = JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, uiD);
			Map<String, List<UimaBioCAnnotation>> spans = new HashMap<String, List<UimaBioCAnnotation>>();
			for (UimaBioCAnnotation a : annotations) {
				Map<String, String> inf = UimaBioCUtils.convertInfons(a.getInfons());
				if (inf.get("type").equals("rubicon") && inf.get("value").equals("clause")
						&& inf.containsKey("scidp-fig-assignment") && inf.get("scidp-fig-assignment").length() > 2) {
					String elStr = inf.get("scidp-fig-assignment");
					String[] elArray = elStr.split("\\|");
					for (String el : elArray) {
						List<UimaBioCAnnotation> aList = new ArrayList<UimaBioCAnnotation>();
						if (spans.containsKey(el))
							aList = spans.get(el);
						aList.add(a);
						spans.put(el, aList);
					}
				}
			}

			for (String sKey : spans.keySet()) {
				List<UimaBioCAnnotation> aList = spans.get(sKey);

				int startPos = aList.get(0).getBegin();
				int endPos = startPos - 1;

				for (UimaBioCAnnotation a : aList) {
					if (a.getBegin() != endPos + 1) {
						UimaBioCAnnotation evFrg = addEvidenceFragmentToJCas(jCas, uiD, sKey, startPos, endPos);
						startPos = a.getBegin();
					}
					endPos = a.getEnd();
				}
				UimaBioCAnnotation evFrg = addEvidenceFragmentToJCas(jCas, uiD, sKey, startPos, endPos);
			}

			List<UimaBioCAnnotation> floats = new ArrayList<UimaBioCAnnotation>();
			for (UimaBioCAnnotation a : JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, uiD)) {
				Map<String, String> infons = UimaBioCUtils.convertInfons(a.getInfons());
				if (infons.containsKey("position") && infons.get("position").equals("float")) {
					floats.add(a);
				}
			}

			for (UimaBioCAnnotation fc : floats) {

				String fig = "";
				String subFig = "-";
				String oldSubFig = "-";
				Matcher m = figPatt.matcher(fc.getCoveredText());
				if (m.find()) {
					fig = m.group(2);
				}

				if (fig.equals(""))
					continue;

				Map<String, String> infons = UimaBioCUtils.convertInfons(fc.getInfons());
				if (infons.get("type").equals("formatting") && infons.get("value").equals("fig")) {

					int begin = fc.getBegin();
					int end = fc.getBegin();
					for (Sentence s : JCasUtil.selectCovered(Sentence.class, fc)) {

						String sText = this.readTokenizedText(jCas, s);
						Matcher m2 = figPatt.matcher(sText);
						if (m2.find()) {
							begin = s.getBegin();
							end = s.getEnd();
							continue;
						}

						String subFigCode = readSubFigCodes(jCas, s);
						if (!subFigCode.equals("-"))
							subFig = subFigCode;

						if (!subFig.equals(oldSubFig)) {
							if (!oldSubFig.equals("-")) {
								UimaBioCAnnotation subfigCaption = addFigureCaptionToJCas(jCas, uiD, fig + oldSubFig,
										begin, end);
								begin = end + 1;
							}

							oldSubFig = subFig;

						}

						end = s.getEnd();

					}
					if (oldSubFig.equals("-"))
						oldSubFig = "";

					UimaBioCAnnotation subfigCaption = addFigureCaptionToJCas(jCas, uiD, fig + oldSubFig, begin, end);
					begin = end + 1;

				}

			}
			
			BioCDocument d = UimaBioCUtils.convertUimaBioCDocument(uiD, jCas);
			d.getInfons().put("rdfs:label","PMID: " + d.getID());
			d.getPassages().get(0).getInfons().put("rdfs:label", "Full text for PMID: " + d.getID());
			
			this.ontModel = ModelFactory.createOntologyModel( OntModelSpec.OWL_MEM );

			BioCLinkedDataUtils.convertBioCDocumentToLinkedData(this.ontModel, d, "http://purl.org/ske/semsci17/");

			File tempFile1 = new File(this.tempDirPath+"/"+d.getID()+"_bioc.ttl");
			PrintWriter out;
			try {
			
				out = new PrintWriter(new BufferedWriter(
						new FileWriter(tempFile1, true)));
				ontModel.write(out, "TTL");
				out.close();

			} catch (IOException e) {
				throw new AnalysisEngineProcessException(e);
			}
			
		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}

	private UimaBioCAnnotation addEvidenceFragmentToJCas(JCas jCas, UimaBioCDocument uiD, String sKey, int startPos,
			int lastEndPos) {
		Map<String, String> inf = new HashMap<String, String>(MapCreate.asMap(
				new String[] { 
						"rdf:type", 
						"rdfs:label", 
						"cito:citesAsEvidence" },
				new String[] { 
						"http://purl.org/spar/doco/TextChunk", 
						"Evidence Fragment: pmid "+ uiD.getId()+", fig "+sKey,
						"http://www.ncbi.nlm.nih.gov/pubmed/" + uiD.getId() + "#" + sKey + " a http://purl.org/spar/doco/FigureLabel"}));
		UimaBioCAnnotation spanA = UimaBioCUtils.createNewAnnotation(jCas, startPos, lastEndPos, inf);
		
		String txt = UimaBioCUtils.readTokenizedText(jCas, spanA);
		spanA.setText(txt);

		return spanA;
	}

	private UimaBioCAnnotation addFigureCaptionToJCas(JCas jCas, UimaBioCDocument uiD, String sKey, int startPos,
			int lastEndPos) {
		
		String fId = sKey.toLowerCase();
		if(!fId.startsWith("f"))
			fId = "f" + fId;
 		Map<String, String> inf = new HashMap<String, String>(MapCreate.asMap(
				new String[] { 
						"rdf:type", 
						"rdfs:label", 
						"cito:describes" },
				new String[] { 
						"http://purl.org/spar/doco/TextChunk", 
						"Figure Legend: pmid "+ uiD.getId()+", fig " + sKey,
						"http://www.ncbi.nlm.nih.gov/pubmed/" + uiD.getId() + "#" + fId + " a http://purl.org/spar/doco/FigureLabel"}));
		//Map<String, String> inf = new HashMap<String, String>(
		//		MapCreate.asMap(new String[] { "rdf:type" }, new String[] { "doco:FigureLabel" }));
		UimaBioCAnnotation figCapA = UimaBioCUtils.createNewAnnotation(jCas, startPos, lastEndPos, inf);
		
		String txt = UimaBioCUtils.readTokenizedText(jCas, figCapA);
		figCapA.setText(txt);

		return figCapA;
	}

	private String readSubFigCodes(JCas jCas, Sentence s) {

		String exptCode = "-";
		String figFrag = s.getCoveredText();
		for (Pattern patt : this.subFigPatt) {
			Matcher m = patt.matcher(figFrag);
			if (m.find()) {
				return m.group(1);
			}
		}
		return exptCode;
	}

	private String readTokenizedText(JCas jCas, Sentence s) {
		String txt = "";
		for (Token t : JCasUtil.selectCovered(jCas, Token.class, s)) {
			txt += t.getCoveredText() + " ";
		}
		if (txt.length() == 0)
			return txt;
		return txt.substring(0, txt.length() - 1);
	}

	@Override
	public void collectionProcessComplete() throws AnalysisEngineProcessException {

		/*File tempFile = new File(outFile.getPath() + ".ttl");
		PrintWriter out;
		try {
		
			out = new PrintWriter(new BufferedWriter(
					new FileWriter(tempFile, true)));
			ontModel.write(out, "TTL");
			out.close();

		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}*/

		/*
		 * Gson gson = new Gson(); 
		 * String json = gson.toJson(d);
		 * 
		 * PrintWriter out = new PrintWriter(new BufferedWriter( new
		 * FileWriter(outFile, true)));
		 * 
		 * out.write(json);
		 * 
		 * out.close();
		 */
		//this.collection = new BioCCollection();

		System.out.print("DONE DONE DONE");

	}

}
