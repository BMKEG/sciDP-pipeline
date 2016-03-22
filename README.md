This system is specfically designed to prepare data for the 'Science Discourse Parser' [https://github.com/edvisees/sciDP](https://github.com/edvisees/sciDP) from Ed Hovy's group at CMU. These are UIMA pipelines that  process open access articles from PubMed Central to generate split-clause data to be processed by sciDP. We also provide scripts to facilitate gathering training data from human annotations and export functions to our preferred output formats. 

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

3. Edit `runPipeline.sh` by adding the full path to the assigned part of the script

4. Clone and build this library

 ```Shell
 git clone https://github.com/BMKEG/sciDP-pipeline
 cd sciDP-pipeline
 mvn -DskipTests clean assembly:assembly
 ```
 
5. Run `runPipeline.sh` for folder `/path/to/folder/we/want/nxml` (nxml contains OA files from PMC)

 ```Shell
 ./runPipeline /path/to/folder/we/want/ #nThreads 
 ```
Where `#nThreads` is the number of threads we want to run the preprocessing pipeline on. 

This should run to generate a number of files in subfolders. These are:

* bioc				
* preprocessed_bioc_results
* disSeg_input_results		
* nxml2txt			
* tsv_results

The input files for the main `sciDP` system are in the `disSeg_input_results` folder 
