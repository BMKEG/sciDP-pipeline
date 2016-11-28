This system is specfically designed to prepare data for the 'Science Discourse Tagger' [https://github.com/BMKEG/sciDT](https://github.com/BMKEG/sciDT) orignally developed by Ed Hovy's group at CMU. These are UIMA pipelines that process open access articles from PubMed Central to generate split-clause data to be processed by sciDT. We also provide scripts to facilitate gathering training data from human annotations and export functions to our preferred output formats. 

## Instructions for Mac

1. Install the necessary dependencies for `nxml2txt`:

 ```Shell
 sudo port install texlive-latex texlive-latex-recommended texlive-latex-extra py-lxml
 ```

2. Install `nxml2txt` and add the binary to your `$PATH`:

 ```Shell
 git clone https://github.com/spyysalo/nxml2txt.git
 cd nxml2txt
 chmod 755 nxml2txt nxml2txt.sh
 ```

3. Clone and build this library

 ```Shell
 git clone https://github.com/BMKEG/sciDT-pipeline
 cd sciDT-pipeline
 mvn -DskipTests clean assembly:assembly
 ```

This will build a fully assembled jar file here: 

4. Running the system is best performed using the provided shell script that executes the `edu.isi.bmkeg.sciDT.bin.SciDT_0_Nxml2SciDT` class. 

 ```Shell
 ./runPipeline /path/to/folder/ #nThreads /path/to/nxml2txt/executable
 ```
Where `#nThreads` is the number of threads we want to run the preprocessing pipeline on. 

This should run to generate a number of files in subfolders. These are:

* nxml2txt			
* bioc				
* preprocessed_bioc_results
* scidt		
* tsv

The input files for the main `sciDT` system are in (A) the `scidt` folder and (B) the `tsv` folder
