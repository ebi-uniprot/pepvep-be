package uk.ac.ebi.protvar.input.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.format.coding.HGVSc;
import uk.ac.ebi.protvar.input.format.genomic.Gnomad;
import uk.ac.ebi.protvar.input.format.genomic.HGVSg;
import uk.ac.ebi.protvar.input.format.genomic.VCF;
import uk.ac.ebi.protvar.input.format.id.ClinVarID;
import uk.ac.ebi.protvar.input.format.id.CosmicID;
import uk.ac.ebi.protvar.input.format.id.DbsnpID;
import uk.ac.ebi.protvar.input.format.protein.HGVSp;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.input.type.ProteinInput;
import uk.ac.ebi.protvar.utils.FetcherUtils;
import uk.ac.ebi.protvar.utils.HGVS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class InputProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(InputProcessor.class);


    /**
     * Takes a list of input strings and return corresponding list of userInput objects
     * @param inputs
     * @return
     */
    public List<UserInput> parse(List<String> inputs) {
        List<UserInput> userInputs = new ArrayList<>();
        inputs.stream().map(String::trim)
                .filter(i -> !i.isEmpty())
                .filter(i -> !i.startsWith("#")).forEach(input -> {
                    UserInput pInput = parse(input);
                    userInputs.add(pInput);
                });
        return userInputs;
    }


    /**
     * Parse input string into a UserInput object.
     * @param inputStr	Null and empty inputs will have been filtered before calling this function.
     *             Input string will have been trimmed as well.
     * @return
     */
    public UserInput parse(String inputStr) {
        if (inputStr == null || inputStr.isEmpty())
            return null;

        // Steps:
        // LEVEL 1 (first-level) CHECK
        // Only check if input meets "general" pattern for a specific type for e.g. if input starts with a certain
        // prefix, for e.g. XX, we assume it is of a specific type.
        // This happens at the top-level "if" condition below, before using the appropriate parser for full parsing.
        // LEVEL 2 (second-level) CHECK
        // Full parsing of input string to extract all attributes happens in L2.
        // Here we may find that we are dealing with a specific input type, but it doesn't fully parse (or contain all
        // the info we require)

        // ID/ref checks - these should be single-word input
        if (DbsnpID.startsWithPrefix(inputStr)) {
            return DbsnpID.parse(inputStr);
        }
        else if (ClinVarID.startsWithPrefix(inputStr)) {
            return ClinVarID.parse(inputStr);
        }
        else if (CosmicID.startsWithPrefix(inputStr)) {
            return CosmicID.parse(inputStr);
        }
        // HGVS inputs
        // general pattern _:_
        //               /    \
        //          REF_SEQ   VAR_DESC
        else if (HGVS.generalRefSeqVarDesc(inputStr)) {
            if (HGVSg.startsWithPrefix(inputStr)) {
                // process as HGVS genomic
                return HGVSg.parse(inputStr);
            }
            if (HGVSc.startsWithPrefix(inputStr)) {
                // process as HGVS cDNA
                return HGVSc.parse(inputStr);
            }
            if (HGVSp.startsWithPrefix(inputStr)) {
                // process as HGVS protein
                return HGVSp.parse(inputStr);
            }
            return HGVS.invalid(inputStr);
        }
        // GNOMAD ID check
        // single-word input
        // pattern: _-_-_-_ (4 dash-sep values)
        else if (Gnomad.preCheck(inputStr)) { // (Gnomad.isValid(inputStr)) {
            return Gnomad.parse(inputStr);
        }
        // Remaining input formats at this point
        // - custom protein -> always starts with an accession
        // - VCF and generic/custom genomic -> always starts with a chr
        else if (ProteinInput.startsWithAccession(inputStr)) {
            return ProteinInput.parse(inputStr);
        }
        else if (GenomicInput.startsWithChromo(inputStr)) {
            // VCF if input sticks to strict format, otherwise, maybe custom
            if (VCF.isValid(inputStr)) {
                return VCF.parse(inputStr);
            }
            else if (GenomicInput.isValid(inputStr)) {
                return GenomicInput.parse(inputStr);
            }
        }
        return GenomicInput.invalidInput(inputStr); // default (or most common) input is expected to be genomic, so
        // let's assume any invalid input is of GenomicInput type.
    }

    public String summary(List<UserInput> userInputs) {
        String inputSummary = String.format("Processed %d input%s ", userInputs.size(), FetcherUtils.pluralise(userInputs.size()));
        int[] counts = {0,0,0,0}; //genomic, coding, protein, ID
        List<String> invalidInputs = new ArrayList<>();


        userInputs.stream().forEach(input -> {
            if (input.getType() == Type.GENOMIC) counts[0]++;
            else if (input.getType() == Type.CODING) counts[1]++;
            else if (input.getType() == Type.PROTEIN) counts[2]++;
            else if (input.getType() == Type.ID) counts[3]++;

            if (!input.isValid()) {
                String invalidMsg = String.format("Invalid input (%s): %s ", input.getInputStr(), Arrays.toString(input.getErrors().toArray()));
                invalidInputs.add(invalidMsg);
                LOGGER.warn(invalidMsg);
            }
        });
        List<String> inputTypes = new ArrayList<>();
        if (counts[0] > 0) inputTypes.add(String.format("%d genomic", counts[0]));
        if (counts[1] > 0) inputTypes.add(String.format("%d cDNA", counts[1]));
        if (counts[2] > 0) inputTypes.add(String.format("%d protein", counts[2]));
        if (counts[3] > 0) inputTypes.add(String.format("%d ID", counts[3]));

        if (inputTypes.size() > 0) inputSummary += "(" + String.join(", ", inputTypes) + ")";

        if (invalidInputs.size() > 0) {
            inputSummary += String.format("%d input%s %s not valid", invalidInputs.size(), FetcherUtils.pluralise(invalidInputs.size()), FetcherUtils.isOrAre(invalidInputs.size()));
        }

        return inputSummary;
    }

}
