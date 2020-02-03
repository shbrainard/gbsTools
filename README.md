# gbsTools

## CopyBarcodes

The CopyBarcodes class performs pre-alignment processing of FASTQ files containing paired-end GBS reads (e.g., those generated on Illumina machines).  The two goals are to identify, and optionally correct, sequencing errors in the barcode/overhang region of the forward reads, and subsequently add correct(ed) barcodes to the beginning of the respective reverse reads.

In all cases, an initial verification step is performed, using a prefix tree to check that the beginning of each read in the forward-read FASTQ file contains one of the barcodes in the supplied barcode file, and that this barcode is immediately followed by one of the two possible overhang sequences that would be genereated by the utilized restriction enzyme (specified in the .config file; by default, the overhangs associated with ApeK1 are used).  THe x-y coordinates of the reverse read are also verified as being identical to the forward read.

If only forward- and reverse- read FASTQ files, plus barcode and configuration files, are passed, mismatches are deleted from the output FASTQ files, which is written to disc as an interleaved .gz archive.

If the "fuzzy" argument is passed, fuzzy-matching is performed in cases of mismatches, and if a unique barcode can be recovered by changing no more than one low-quality, it is written to the output file as well (the specific threshold is defined in the .config file, and is set 'F' by default).  This option can also be run in "debug mode" by calling "fuzzy debug": all skipped reads will then be writted to debugOut.txt, and some summary statistics will also be output describing what was skipped.

Example usage:

```bash
export forwardReads=/PATH/TO/FORWARD/READ/FASTQ/GZ/FILE
export reverseReads=/PATH/TO/REVERSE/READ/FASTQ/GZ/FILE
export config=/PATH/TO/CONFIG/DOT/FILE
export barcodes=/PATH/TO/BARCODE/TXT/FILE

java -cp gbsTools.jar CopyBarcodes $forwardReads $reverseReads $barcodes $config [fuzzy|fuzzy debug]
```
Benchmarking was performed on a MacBook Pro with a 3.3 GHz Intel Core i7 CPU and 16 GB of 2133 MHz LPDDR3 RAM.  The forward-read file was 28.64 GB, and the reverse-read file was 29.74 GB (each containing roughly 400,000,000 reads).  Because writing to the output file is speed limiting, calling the .jar file with or without fuzzy matching took approximately 450 minutes.


## Demultiplexing

This class performs similar quality control steps as described above (x-y coordinate checking of reverse reads, and fuzzy matching of barcodes), but instead of a single interleaved FASTQ being output, pairs of forward- and reverse-read FASTQ files are written for each unique barcode.  The barcode file itself should include a tab-separated sample ID, which will act as the sample name following the naming conventions described here: http://www.ddocent.com/UserGuide/#raw-sequences

In addition a population name (a string) should be passsed as the first argument following the class path:

```bash

java -cp gbsTools.jar Demultiplexer <populationName> $forwardReads $reverseReads $barcodes $config [fuzzy|fuzzy debug]
```
Benchmarking was performed on a CentOS Linux distribution, running on a server with 6 Intel Xeon 2.67 GHz CPUs and 40 GB of RAM.  The same two FASTQ files as above were de-multiplexed into their component 192 FASTQ files (each ~200 MB when gzipped) in 160 minutes.


