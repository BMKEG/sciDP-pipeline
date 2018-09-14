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

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.ConfigurationParameterFactory;
import org.uimafit.util.JCasUtil;

import com.google.gson.Gson;

import bioc.BioCAnnotation;
import bioc.BioCCollection;
import bioc.BioCDocument;
import bioc.BioCLocation;
import bioc.esViews.BioCAnnotation.BioCAnnotation__BioCLocation;
import bioc.esViews.BioCPassage.BioCPassage__BioCAnnotation;
import bioc.esViews.BioCPassage.BioCPassage__BioCDocument;
import bioc.esViews.BioCPassage.BioCPassage__BioCLocation;
import bioc.esViews.BioCPassage.BioCPassage__BioCPassage;
import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import edu.isi.bmkeg.uimaBioC.UimaBioCUtils;

public class SaveAsEvidenceFragments extends JCasAnnotator_ImplBase {

	public final static String PARAM_FILE_PATH = ConfigurationParameterFactory
			.createConfigurationParameterName(SaveAsEvidenceFragments.class, "outFilePath");
	@ConfigurationParameter(mandatory = true, description = "The place to put the document files to be classified.")
	String outFilePath;
	private File outFile;

	public static String JSONL = ".jsonl";

	private BioCCollection collection;
	private Map<String, String> json_lines;

	public void initialize(UimaContext context) throws ResourceInitializationException {

		super.initialize(context);

		this.outFilePath = (String) context.getConfigParameterValue(PARAM_FILE_PATH);
		this.outFile = new File(this.outFilePath);

		this.collection = new BioCCollection();

	}

	public void process(JCas jCas) throws AnalysisEngineProcessException {

		UimaBioCDocument uiD = JCasUtil.selectSingle(jCas, UimaBioCDocument.class);
		if (uiD.getId().equals("skip"))
			return;
		
		try {

			if (uiD.getId().equals("skip")) {
				return;
			}

			this.json_lines = new HashMap<String, String>();

			// BioCCollection c = new BioCCollection();
			BioCDocument d = UimaBioCUtils.convertUimaBioCDocument(uiD, jCas);
			BioCPassage__BioCDocument dd = new BioCPassage__BioCDocument();
			dd.setId(d.getID());
			dd.setInfons(removeInfonsTypeValue(d.getInfons()));

			List<UimaBioCAnnotation> allAnn = JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, uiD);
			for (UimaBioCAnnotation evFrg : allAnn) {

				Map<String, String> inf = UimaBioCUtils.convertInfons(evFrg.getInfons());
				if (!inf.get("type").equals("evidence-fragment") && 
						!inf.get("type").equals("subfigure-caption"))
					continue;
				String type = inf.get("type");
				inf.put( "subfig", inf.get("value") );
				inf.remove("value");
				
				BioCPassage__BioCPassage pp = new BioCPassage__BioCPassage();
				pp.setInfons(inf);
				
				int passage_offset = evFrg.getLocations(0).getOffset();
				pp.setOffset( passage_offset );
				pp.setText(evFrg.getCoveredText());
				pp.setDocument(dd);

				List<UimaBioCAnnotation> frgAnnotations = JCasUtil.selectCovered(jCas, UimaBioCAnnotation.class, evFrg);
				for (UimaBioCAnnotation frgAnn : frgAnnotations) {
					
					Map<String, String> inf2 = UimaBioCUtils.convertInfons(frgAnn.getInfons());
					if (!inf2.get("type").equals("entity"))
						continue;

					BioCAnnotation a = UimaBioCUtils.convertUimaBioCAnnotation(frgAnn, jCas);

					BioCPassage__BioCAnnotation aa = new BioCPassage__BioCAnnotation();
					aa.setId(a.getID());
					aa.setInfons(removeInfonsTypeValue(a.getInfons()));
					aa.setText(a.getText());
					List<BioCPassage__BioCLocation> llist = new ArrayList<BioCPassage__BioCLocation>();
					for (BioCLocation l : a.getLocations()) {
						llist.add(new BioCPassage__BioCLocation(l.getOffset()-passage_offset, l.getLength()));
					}
					aa.setLocations(llist);
					pp.getAnnotations().add(aa);
				}

				Gson gson = new Gson();
				json_lines.put(d.getID() + "__" + inf.get("subfig") + "__" + inf.get("type"), gson.toJson(pp));

			}

			String relPath = d.getInfon("relative-source-path").replaceAll("\\.txt", "") + JSONL;
			File outFile = new File(outFilePath + "/" + relPath);
			if (!outFile.getParentFile().exists()) {
				outFile.getParentFile().mkdirs();
			}

			if (outFile.exists())
				outFile.delete();
			
			PrintWriter out = null;
			try {

				out = new PrintWriter(new BufferedWriter(new FileWriter(outFile, true)));
				for (String key : this.json_lines.keySet()) {
					out.write(key + ":\t" + this.json_lines.get(key) + "\n");
				}
				out.close();

			} catch (IOException e) {
				e.printStackTrace();
				throw new AnalysisEngineProcessException(e);
			}


		} catch (Exception e) {

			throw new AnalysisEngineProcessException(e);

		}

	}
	
	private Map<String,String> removeInfonsTypeValue(Map<String,String> inf) {
		inf.put( inf.get("type"), inf.get("value") );
		inf.remove("type");
		inf.remove("value");
		return inf;
	}
	
	
//	public void collectionProcessComplete() throws AnalysisEngineProcessException {
//	}

}
