/*
 * Copyright (c) 2016 The Ontario Institute for Cancer Research. All rights reserved.
 *                                                                                                               
 * This program and the accompanying materials are made available under the terms of the GNU Public License v3.0.
 * You should have received a copy of the GNU General Public License along with                                  
 * this program. If not, see <http://www.gnu.org/licenses/>.                                                     
 *                                                                                                               
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY                           
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES                          
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT                           
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,                                
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED                          
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;                               
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER                              
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN                         
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.icgc.dcc.imports.gene.writer;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.regex.Pattern;

import org.icgc.dcc.common.core.model.ReleaseCollection;
import org.icgc.dcc.imports.core.util.AbstractJongoWriter;
import org.jongo.MongoCollection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.MongoClientURI;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GeneWriter extends AbstractJongoWriter<ObjectNode> {

  /**
   * Constants
   */
  private static final int STATUS_GENE_COUNT = 10000;
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern TSV = Pattern.compile("\t");

  /**
   * Dependencies
   */
  private final BufferedReader bufferedReader;

  private int counter = 0;
  private MongoCollection geneCollection;

  public GeneWriter(@NonNull MongoClientURI mongoUri, @NonNull BufferedReader bufferedReader) {
    super(mongoUri);
    this.bufferedReader = bufferedReader;
    this.geneCollection = getCollection(ReleaseCollection.GENE_COLLECTION);
    this.geneCollection.drop();
  }

  @SneakyThrows
  public void consumeGenes() {
    log.info("CONSUMING GENES");
    ObjectNode geneNode = null;
    ObjectNode curTranscript = null;
    ArrayNode transcripts = MAPPER.createArrayNode();
    ArrayNode exons = MAPPER.createArrayNode();

    for (String s = bufferedReader.readLine(); null != s; s = bufferedReader.readLine()) {
      s = s.trim();
      if (s.length() > 0) {

        if (s.charAt(0) == 35) {
          if (s.startsWith("##fasta")) {
            break;
          }
        } else {
          val entry = parseLine(s);
          if (entry != null) {

            if (entry.get("type").asText().equals("gene")) {
              if (geneNode != null) {
                transcripts.add(curTranscript);
                geneNode.put("transcripts", transcripts);
                writeFiles(geneNode);
                transcripts = MAPPER.createArrayNode();
              }
              geneNode = constructGeneNode(entry);
            } else if (entry.get("type").asText().equals("transcript")) {
              if (curTranscript != null) {
                curTranscript.put("exons", exons);
                try {
                  transcripts.add(postProcessTranscript(curTranscript, geneNode.get("strand").asText()));
                } catch (Exception e) {
                  log.error(curTranscript.toString());
                  throw e;
                }
                exons = MAPPER.createArrayNode();
              }
              curTranscript = constructTranscriptNode(entry);
            } else if (entry.get("type").asText().equals("exon")) {
              exons.add(constructExonNode(entry));
            } else if (entry.get("type").asText().equals("CDS")) {
              ((ObjectNode) exons.get(exons.size() - 1)).put("cds", entry);
            } else if (entry.get("type").asText().equals("start_codon")) {
              curTranscript.put("start_exon", exons.size() - 1);
            } else if (entry.get("type").asText().equals("stop_codon")) {
              curTranscript.put("end_exon", exons.size() - 1);
            }

          }
        }
      }
    }
  }

  @Override
  public void writeFiles(@NonNull ObjectNode value) {
    if (++counter % STATUS_GENE_COUNT == 0) {
      log.info("Writing {}", counter);
    }
    this.geneCollection.insert(value);
  }

  private ObjectNode parseLine(@NonNull String s) {
    String[] line = TSV.split(s);
    val seqname = line[0].trim();
    val source = line[1].trim();
    val type = line[2].trim();
    String locStart = line[3].trim();
    String locEnd = line[4].trim();

    Double score;
    try {
      score = Double.parseDouble(line[5].trim());
    } catch (Exception var15) {
      score = 0.0D;
    }

    char strand = line[6].trim().charAt(0);
    int locationStart = Integer.parseInt(locStart);
    int locationEnd = Integer.parseInt(locEnd);
    if (locationStart > locationEnd) {
      int location = locationStart;
      locationStart = locationEnd;
      locationEnd = location;
    }

    val negative = locationStart <= 0 && locationEnd <= 0;
    assert strand == 45 == negative;

    int frame;
    try {
      frame = Integer.parseInt(line[7].trim());
    } catch (Exception var14) {
      frame = -1;
    }

    val attributes = line[8];
    val attributeMap = parseAttributes(attributes);

    ObjectNode feature = MAPPER.createObjectNode();

    int strandNumber = 0;
    if (strand == '+') {
      strandNumber = 1;
    } else if (strand == '-') {
      strandNumber = -1;
    }

    feature.put("seqname", seqname);
    feature.put("source", source);
    feature.put("type", type);
    feature.put("locationStart", locationStart);
    feature.put("locationEnd", locationEnd);
    feature.put("score", score);
    feature.put("strand", strandNumber);
    feature.put("frame", frame);

    for (val kv : attributeMap.entrySet()) {
      feature.put(kv.getKey(), kv.getValue());
    }

    return feature;
  }

  private static HashMap<String, String> parseAttributes(String attributes) {
    val attributeMap = new HashMap<String, String>();

    String[] tokens = attributes.split(";");
    for (val token : tokens) {
      String[] kv = token.trim().replace("\"", "").split("\\s+");
      attributeMap.put(kv[0], kv[1]);
    }
    return attributeMap;
  }

  private static ObjectNode constructGeneNode(ObjectNode data) {
    val gene = MAPPER.createObjectNode();
    gene.put("_gene_id", data.get("gene_id").asText());
    gene.put("symbol", data.get("gene_name").asText());
    gene.put("biotype", data.get("gene_biotype").asText());
    gene.put("chomosome", data.get("seqname").asText());
    gene.put("strand", data.get("strand").asText());
    gene.put("start", data.get("locationStart").asInt());
    gene.put("end", data.get("locationEnd").asInt());
    return gene;
  }

  private static ObjectNode constructTranscriptNode(ObjectNode data) {
    val transcript = MAPPER.createObjectNode();
    transcript.put("id", data.get("transcript_id").asText());
    transcript.put("name", data.get("transcript_name").asText());
    transcript.put("biotype", data.get("transcript_biotype").asText());
    transcript.put("start", data.get("locationStart").asInt());
    transcript.put("end", data.get("locationEnd").asInt());
    return transcript;
  }

  private static ObjectNode constructExonNode(ObjectNode data) {
    val exon = MAPPER.createObjectNode();
    exon.put("start", data.get("locationStart").asInt());
    exon.put("end", data.get("locationEnd").asInt());

    return exon;
  }

  private static ObjectNode postProcessTranscript(ObjectNode transcript, String strand) {
    val exons = (ArrayNode) transcript.get("exons");
    transcript.put("coding_region_start", 0);
    transcript.put("coding_region_end", 0);
    transcript.put("cdna_coding_start", 0);
    transcript.put("cdna_coding_end", 0);

    int preExonCdnaEnd = 0;
    for (JsonNode exon : exons) {
      ObjectNode exonNode = (ObjectNode) exon;

      val exonLength = exon.get("end").asInt() - exon.get("start").asInt();
      exonNode.put("cdna_start", preExonCdnaEnd + 1);
      exonNode.put("cdna_end", exonNode.get("cdna_start").asInt() + exonLength - 1);
      preExonCdnaEnd = exonNode.get("cdna_end").asInt();

      exonNode.put("genomic_coding_start", 0);
      exonNode.put("genomic_coding_end", 0);
      exonNode.put("cdna_coding_start", 0);
      exonNode.put("cdna_coding_end", 0);
    }

    val startExon = transcript.path("start_exon");
    val endExon = transcript.path("end_exon");

    if (startExon.isMissingNode() || endExon.isMissingNode()) {
      return transcript;
    }

    for (int i = startExon.asInt(); i <= endExon.asInt(); i++) {
      val exon = (ObjectNode) exons.get(i);
      exon.put("genomic_coding_start", exon.get("start").asInt());
      exon.put("cdna_coding_start", exon.get("cdna_coding_start").asInt());
      exon.put("genomic_coding_end", exon.get("end").asInt());
      exon.put("cdna_coding_end", exon.get("cdna_end").asInt());

      if (i == startExon.asInt()) {
        if (strand.equals("-1")) {
          val end = exon.get("cds").get("locationStart").asInt();
          transcript.put("coding_region_end", end);
          exon.put("genomic_coding_end", end);
          exon.put("cdna_coding_end", end - exon.get("start").asInt() - 1);
        } else {
          val start = exon.get("cds").get("locationStart").asInt();
          transcript.put("coding_region_start", start);
          exon.put("genomic_coding_start", start);
          exon.put("cdna_coding_start", start - exon.get("start").asInt() + 1);
        }
        transcript.put("cdna_coding_start", exon.get("cdna_coding_start").asInt());
      }

      if (i == endExon.asInt()) {
        val cds = exon.path("cds");

        if (strand.equals("-1")) {
          if (cds.isMissingNode()) {
            val start = exons.get(i - 1).path("cds").get("locationEnd").asInt();
            transcript.put("coding_regionStart", start);
          } else {
            val start = cds.get("locationEnd").asInt() + 1;
            transcript.put("coding_region_start", start);
            exon.put("genomic_coding_start", start);
          }
        } else {
          if (cds.isMissingNode()) {
            val end = exons.get(i - 1).path("cds").get("locationEnd").asInt();
            transcript.put("coding_region_end", end);
          } else {
            val end = cds.get("locationEnd").asInt();
            transcript.put("coding_region_end", end);
            exon.put("genomic_coding_end", end);
            exon.put("cdna_coding_end",
                end - exon.get("genomic_coding_start").asInt() + exon.get("cdna_start").asInt() + 1);
          }
        }

        if (cds.isMissingNode()) {
          transcript.put("cdna_coding_end", exons.get(i - 1).get("cdna_coding_end").asInt());
        } else {
          transcript.put("cdna_coding_end", exon.get("cdna_coding_end").asInt());
        }
      }

    }

    transcript.put("start_exon", startExon.asInt());
    transcript.put("end_exon", endExon.asInt());

    return transcript;
  }

}
