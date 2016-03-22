# sciDP-preprocessing

This system is specfically designed to prepare data for the 'Science Discourse Parser' [https://github.com/edvisees/sciDP](https://github.com/edvisees/sciDP) from Ed Hovy's group at CMU. These are UIMA pipelines that  process open access articles from PubMed Central to generate split-clause data to be processed by sciDP. We also provide scripts to facilitate gathering training data from human annotations and export functions to our preferred output formats. 

## Installation Instructions for Mac

1. Install `python` 2.7 and set it as the default:

 ```Shell
 sudo port install python27
 sudo port select --set python python27
 ```

2. Install the necessary dependencies for `nxml2txt`:

 ```Shell
 sudo port install texlive-latex texlive-latex-recommended texlive-latex-extra py-lxml
 ```

3. Install `nxml2txt` and add the binary to your `$PATH`:

 ```Shell
 git clone https://github.com/spyysalo/nxml2txt.git
 cd nxml2txt
 chmod 755 nxml2txt nxml2txt.sh
 export PATH=<PATH WHERE nxml2txt IS INSTALLED>:$PATH
 ```

## Script

We provide a shell script to execute the 


