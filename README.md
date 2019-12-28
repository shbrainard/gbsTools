# gbs-tools

CopyBarcodes.jar is a utility for performing initial pre-processing of FASTQ files generated from a paired-end run on Illumina
machines to deal with sequencing errors in the barcode region of the read, and the absence of barcodes in the reverse-read
FASTQ file.

First, a verification step is performed, checking that the beginning of each read in the forward-read FASTQ file contains 
a barcode from the supplied barcode file, immediately followed by the overhang genereated by the ApeK1 restriction enzyme.

If only forward and reverse reads, plus a barcode file, is passed, mismatches are deleted from the forward and reverse-read
FASTQ files.

If "fuzzy" is passed as an option, fuzzy-matching is performed in cases of mismatches, if a unique barcode 
can be identified by changing no more than one low-quality (lower than quality score of F).

If "retain" is passed, mismatches are not removed from either the forward file, and are copied as is to the reverse
read FASTQ file

Example usage:

```bash
export forwardReads = "191016_AHLKHHDMXX/Box-1_S79_L001_R1_001.fastq.gz"
export reverseReads = "191016_AHLKHHDMXX/Box-1_S79_L001_R2_001.fastq.gz"
export barcodes = "191016_AHLKHHDMXX/GBS-ApeKI-1-96_barcodes.txt"

java -cp CopyBarcodes.jar CopyBarcodes $forwardReads $reverseReads $barcodes [fuzzy|retain|nothing]
```

Rough benchmarking was performed on a MacBook Pro with a 3.3 GHz Intel Core i7 CPU and 16 GB of 2133 MHz LPDDR3 RAM.  

retain: ~4hrs
fuzzy: ~9hrs
nothing: ~7.5 hrs
