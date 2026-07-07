process ALIGN_SAMPLE {
    input:
    val sample

    output:
    path "${sample}.aligned.txt"

    script:
    """
    echo "pretend alignment for ${sample}" > ${sample}.aligned.txt
    """
}

workflow {
    channel.of('sampleA', 'sampleB', 'sampleC') | ALIGN_SAMPLE
}
