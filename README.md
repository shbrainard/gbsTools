# gbs-tools

CopyBarcodes performs pre-alignment processing of FASTQ files containing paired-end GBS reads (e.g., those generated on Illumina machines).  The two goals are to identify, and optionally correct, sequencing errors in the barcode/overhang region of the forward reads, and subsequently add correct(ed) barcodes to the beginning of the respective reverse reads.

In all cases, an initial verification step is performed, using a prefix tree to check that the beginning of each read in the forward-read FASTQ file contains one of the barcodes in the supplied barcode file, and that this barcode is immediately followed by one of the two possible overhang sequences that would be genereated by the utilized restriction enzyme (specified in the .config file; by default, the overhangs associated with ApeK1 are used).

If only forward- and reverse- read FASTQ files, plus a barcode file, are passed, mismatches are deleted from the output forward- and reverse-read FASTQ files, which are written to disc as .gz archives.

If the "fuzzy" argument is passed, fuzzy-matching is performed in cases of mismatches, and if a unique barcode can be recovered by changing no more than one low-quality (the specific threshold is defined in the .config file, and is set to lower than F by default), it is also written to the output files as well.  This option can also be run in "debug mode" by calling "fuzzy debug": all skipped reads will then be writted to debugOut.txt, and some summary statistics will also be output describing what was skipped.

If, instead, "retain" is passed, mismatches are not removed from the forward-read file, and are copied as-is to the reverse-read zipped FASTQ file.

Example usage:

```bash
export forwardReads=/PATH/TO/FORWARD/READ/FASTQ/GZ/FILE
export reverseReads=/PATH/TO/REVERSE/READ/FASTQ/GZ/FILE
export config=/PATH/TO/CONFIG/DOT/FILE
export barcodes=/PATH/TO/BARCODE/TXT/FILE


java -cp CopyBarcodes.jar CopyBarcodes $forwardReads $reverseReads $barcodes $config [fuzzy|fuzzy debug|retain]
```
N.B. "fuzzy" and "retain" are mutually-exclusive options.

Benchmarking was performed on a MacBook Pro with a 3.3 GHz Intel Core i7 CPU and 16 GB of 2133 MHz LPDDR3 RAM.  The forward-read file was 28.64 GB, and the reverse-read file was 29.74 GB (each containing roughly 400,000,000 reads).

retain: 250 minutes

default, fuzzy, fuzzy debug: 450 minutes
