package edu.isi.bmkeg.sciDT.drools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.uima.jcas.JCas;
import org.drools.KnowledgeBase;
import org.drools.KnowledgeBaseFactory;
import org.drools.builder.DecisionTableConfiguration;
import org.drools.builder.DecisionTableInputType;
import org.drools.builder.KnowledgeBuilder;
import org.drools.builder.KnowledgeBuilderFactory;
import org.drools.builder.ResourceType;
import org.drools.compiler.DecisionTableFactory;
import org.drools.compiler.PackageBuilderConfiguration;
import org.drools.definition.KnowledgePackage;
import org.drools.io.Resource;
import org.drools.io.ResourceFactory;
import org.drools.runtime.StatelessKnowledgeSession;

import bioc.type.UimaBioCAnnotation;
import bioc.type.UimaBioCDocument;
import edu.isi.bmkeg.utils.Converters;

/**
 * Rule based classification of clauses using drools. 
 */

public class ClauseDroolsClassifier {
	
	private static Logger logger = Logger.getLogger(ClauseDroolsClassifier.class);

	private StatelessKnowledgeSession kSession;
		
	private KnowledgeBase kbase;
	
	private void reportCompiledRules(String droolsFileName, 
			DecisionTableConfiguration dtableconfiguration) throws IOException  {
		
		String rules = DecisionTableFactory.loadFromInputStream(ResourceFactory.newFileResource(droolsFileName).getInputStream(), dtableconfiguration);
		logger.info( "GENERATED RULE FILE FROM SPREADSHEET:\n" + rules);
		
	}
	
	/*
	 * RuleBasedChunkClassifier classfier = new RuleBasedChunkClassifier(
	 * 		ruleFile.getPath(), new RTModelFactory());
	 */
	public ClauseDroolsClassifier(String droolsFileName) throws Exception  {

		// Workaround for JBRULES-3163
		Properties properties = new Properties();
		properties.setProperty( "drools.dialect.java.compiler.lnglevel", "1.6" );
		PackageBuilderConfiguration cfg = new PackageBuilderConfiguration( properties );
		KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder( cfg );
		kbase = KnowledgeBaseFactory.newKnowledgeBase();
		
		File expandedFile = null;
		if( droolsFileName.contains(".jar!") || droolsFileName.contains(".zip!") ) {
			File fileInArchive = new File(droolsFileName);
			expandedFile = Converters.retrieveFileFromArchive(fileInArchive);
			droolsFileName = expandedFile.getPath();
		}
			
		if(droolsFileName.endsWith(".xls")) {
		
			DecisionTableConfiguration dtableconfiguration =
				KnowledgeBuilderFactory.newDecisionTableConfiguration();
			
			dtableconfiguration.setInputType( DecisionTableInputType.XLS );
			
			Resource xlsRes = ResourceFactory.newFileResource( droolsFileName );
			
			kbuilder.add( xlsRes, ResourceType.DTABLE, dtableconfiguration);
			
			reportCompiledRules(droolsFileName,dtableconfiguration);
		
		} else {
			throw new Exception("Need to specify xls file for DROOLS classification");
		}

		if (kbuilder.hasErrors()) {

			if( expandedFile != null ) {
				Converters.recursivelyDeleteFiles(expandedFile.getParentFile());
			}

			throw new ClassificationException("Error in DROOLS run"+ kbuilder.getErrors());
			
		}
		
		ArrayList<KnowledgePackage> kpkgs = new ArrayList<KnowledgePackage>(
				kbuilder.getKnowledgePackages());
		
		kbase.addKnowledgePackages(kpkgs);		
	
	}
	
	public void classifyDocument(JCas jcas, UimaBioCDocument uiD, File ruleFile)
			throws ClassificationException, IOException {

		/*for (int i = 1; i <= clause.getTotalNumberOfPages(); i++) {

			PageBlock page = clause.getPage(i);

			List<ChunkBlock> chunkList = page
					.getAllChunkBlocks(SpatialOrdering.MIXED_MODE);

			this.classify(chunkList);

		}*/

	}

	public void classifyClauses(JCas jcas, List<UimaBioCAnnotation> clauses) throws Exception {
		
		this.kSession = kbase.newStatelessKnowledgeSession();
		for (UimaBioCAnnotation clause : clauses) {
			kSession.setGlobal("clause", clause);
			kSession.execute(new ClauseFeatures(jcas, clause));
		}
		this.kSession = null;

	}

}
