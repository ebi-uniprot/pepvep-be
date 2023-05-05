package uk.ac.ebi.protvar.loader;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.ResourceUtils;

import javax.annotation.PostConstruct;
import java.io.*;
import java.lang.reflect.Type;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;


@Repository
public class PDBeLoader {
    private static final Logger logger = LoggerFactory.getLogger(PDBeLoader.class);

    //@Value(("${protvar.data}"))
    private String downloadDir;

    private Map<String, PDBeStructure[]> pdBeData = new LinkedHashMap<>();

    //@PostConstruct
    public void initialize() {

        try {
            decompressBz2();
        }
        catch (Exception ex) {
            logger.warn("Couldn't load PDBe data.");
        }


    }

    public List<PDBeStructureResidue> getPDBeStructureResidue(String accession, int position) {
        if (pdBeData.containsKey(accession)) {
            List<PDBeStructureResidue> residues = new ArrayList<>();
            PDBeStructure[] structures = pdBeData.get(accession);
            for (PDBeStructure structure : structures) {
                boolean withinRegion = false;
                if (structure.getObserved_regions() != null && structure.getObserved_regions().length > 0) {
                    for (ObservedRegion region : structure.getObserved_regions()) {
                        if (position >= region.getUnp_start() && position <= region.getUnp_end()) {
                            withinRegion = true;
                            break;
                        }
                    }
                }
                if (withinRegion) {
                    residues.add(newStructureResidue(structure, position));
                }
            }
            return residues;
        }
        return Collections.emptyList();
    }

    private PDBeStructureResidue newStructureResidue(PDBeStructure structure, int position) {
        PDBeStructureResidue residue = new PDBeStructureResidue();
        residue.setChain_id(structure.getChain_id());
        residue.setExperimental_method(structure.getExperimental_method());
        residue.setPdb_id(structure.getPdb_id());
        residue.setResolution(structure.getResolution());
        int offset = structure.getStart() - structure.getUnp_start();
        residue.setStart(position + offset);
        return residue;
    }

    private void decompressBz2() throws IOException, ArchiveException {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Double.class, (JsonSerializer<Double>) (src, typeOfSrc, context) -> {
            DecimalFormat df = new DecimalFormat("#.#");
            df.setRoundingMode(RoundingMode.CEILING);
            return new JsonPrimitive(Double.parseDouble(df.format(src)));
        });
        Gson gson = builder.create();

        File f = ResourceUtils.getFile("classpath:pdbe2.tar.bz2");
        InputStream fin = new FileInputStream(f);
        BufferedInputStream in = new BufferedInputStream(fin);
        OutputStream out = Files.newOutputStream(Paths.get("pdbe.tar"));
        BZip2CompressorInputStream bzIn = new BZip2CompressorInputStream(in);
        int buffersize = 2048;
        final byte[] buffer = new byte[buffersize];
        int n = 0;
        while (-1 != (n = bzIn.read(buffer))) {
            out.write(buffer, 0, n);
        }
        out.close();
        bzIn.close();

        final List<File> untaredFiles = new LinkedList<>();
        final InputStream is = new FileInputStream("pdbe.tar");
        final TarArchiveInputStream debInputStream = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", is);
        TarArchiveEntry entry = null;
        while ((entry = (TarArchiveEntry)debInputStream.getNextEntry()) != null) {
            //final File outputFile = new File(entry.getName());
            if (entry.isFile()) {
                Path temp = Files.createTempFile(null, null);
                final OutputStream outputFileStream = new FileOutputStream(temp.toFile());
                IOUtils.copy(debInputStream, outputFileStream);
                outputFileStream.close();

                Type mapType = new TypeToken<Map<String, PDBeStructure[]>>() {}.getType();
                Map<String, PDBeStructure[]> object = gson.fromJson(new FileReader(temp.toFile()), mapType);
                pdBeData.putAll(object);
            }/*
            if (!entry.isDirectory()) {
                final OutputStream outputFileStream = new FileOutputStream(outputFile);
                IOUtils.copy(debInputStream, outputFileStream);
                outputFileStream.close();
            }*/
            //untaredFiles.add(outputFile);
        }
        debInputStream.close();
    }
}
