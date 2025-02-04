package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.ClinVar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
@AllArgsConstructor
public class ClinVarRepo {
    public static final String SELECT_CLINVAR_WHERE_RCV_IN = """
        SELECT DISTINCT c.rcv, c.chr, c.pos, c.ref, c.alt FROM clinvar c
        INNER JOIN (VALUES :rcvs) AS t(rcv)
        ON t.rcv=c.rcv
        """;
    public static final String SELECT_CLINVAR_WHERE_VCV_IN = """
        SELECT DISTINCT c.vcv, c.chr, c.pos, c.ref, c.alt FROM clinvar c
        INNER JOIN (VALUES :vcvs) AS t(vcv)
        ON t.vcv=c.vcv
        """;

    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public List<ClinVar> getByRCV(Set<Object[]> rcvs) {
        if (rcvs == null || rcvs.isEmpty())
            return new ArrayList<>();
        SqlParameterSource parameters = new MapSqlParameterSource("rcvs", rcvs);
        return namedParameterJdbcTemplate.query(SELECT_CLINVAR_WHERE_RCV_IN, parameters, (rs, rowNum) ->
                ClinVar.builder().rcv(rs.getString("rcv"))
                        .chr(rs.getString("chr"))
                        .pos(rs.getInt("pos"))
                        .ref(rs.getString("ref"))
                        .alt(rs.getString("alt"))
                        .build());
    }

    public List<ClinVar> getByVCV(Set<Object[]> vcvs) {
        if (vcvs == null || vcvs.isEmpty())
            return new ArrayList<>();
        SqlParameterSource parameters = new MapSqlParameterSource("vcvs", vcvs);
        return namedParameterJdbcTemplate.query(SELECT_CLINVAR_WHERE_VCV_IN, parameters, (rs, rowNum) ->
                ClinVar.builder().vcv(rs.getString("vcv"))
                        .chr(rs.getString("chr"))
                        .pos(rs.getInt("pos"))
                        .ref(rs.getString("ref"))
                        .alt(rs.getString("alt"))
                        .build());
    }
}