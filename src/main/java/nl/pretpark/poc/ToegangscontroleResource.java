package nl.pretpark.poc;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.api.core.ast.DecisionNode;
import org.kie.dmn.core.internal.utils.DMNRuntimeBuilder;
import org.kie.dmn.model.api.DecisionTable;
import org.kie.dmn.model.api.InputClause;
import org.kie.dmn.model.api.DecisionRule;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class ToegangscontroleResource {

    private static final String NAMESPACE = "https://pretpark.nl/toegangscontrole";
    private static final String MODEL_NAME = "Toegangscontrole";

    private DMNRuntime dmnRuntime;
    private DMNModel dmnModel;

    // Wordt eenmalig uitgevoerd bij het opstarten van de applicatie
    // Hier laad het DMN-bestand in de rule engine zodat het klaar staat voor gebruik
    @PostConstruct
    void init() {
        // DMN-bestand inlezen vanuit de resources-map en omzetten naar een uitvoerbaar model
        dmnRuntime = DMNRuntimeBuilder.fromDefaults()
                .buildConfiguration()
                .fromClasspathResource("toegangscontrole.dmn", getClass())
                .getOrElseThrow(e -> new RuntimeException("Kon DMN-model niet laden", e));
        // Het specifieke model ophalen op basis van namespace en naam zoals gedefinieerd in het DMN-bestand
        dmnModel = dmnRuntime.getModel(NAMESPACE, MODEL_NAME);
    }

    // Geeft alleen de attractienamen terug, gebruikt intern getAttractieDetails()
    @GET
    @Path("/attracties")
    public List<String> getAttracties() {
        return getAttractieDetails().stream()
                .map(AttractieDetail::naam)
                .collect(Collectors.toList());
    }

    // Leest de beslissingstabel uit het DMN-bestand en geeft per attractie de vereisten terug
    @GET
    @Path("/attracties/details")
    public List<AttractieDetail> getAttractieDetails() {
        // De beslissingen ophalen uit het model en de onderliggende tabel uitlezen
        DecisionNode decisionNode = dmnModel.getDecisionByName("Toegangsbeslissing");
        DecisionTable table = (DecisionTable) decisionNode.getDecision().getExpression();

        // Kolomindices opzoeken op naam zodat de volgorde niet uitmaakt
        // KIE Sandbox kan kolommen verplaatsen bij het bewerken, dit vangt dat op
        Map<String, Integer> kolomIndex = new HashMap<>();
        List<InputClause> inputs = table.getInput();
        for (int i = 0; i < inputs.size(); i++) {
            String expr = inputs.get(i).getInputExpression().getText().trim();
            kolomIndex.put(expr, i);
        }

        // Kolomnummers vastleggen per invoerveld, -1 betekent dat de kolom niet bestaat
        int attractieIdx   = kolomIndex.getOrDefault("attractie", 0);
        int leeftijdIdx    = kolomIndex.getOrDefault("leeftijd", 1);
        int lengteIdx      = kolomIndex.getOrDefault("lengte", 2);
        int begeleidingIdx = kolomIndex.getOrDefault("begeleiding", -1);
        int zwangerIdx     = kolomIndex.getOrDefault("zwanger", -1);

        // Alleen de regels met "toegestaan" als uitkomst bevatten de minimumvereisten
        // De geweigerd-regels slaan we over omdat die geen bruikbare grenswaarden hebben
        return table.getRule().stream()
                .filter(rule -> rule.getOutputEntry().get(0).getText().contains("toegestaan"))
                .map(rule -> {
                    // Per attractieregel de vereisten uitlezen uit de invoercellen
                    String naam        = tekstZonderAanhalingstekens(rule.getInputEntry().get(attractieIdx).getText());
                    int minLeeftijd    = parseMinWaarde(rule.getInputEntry().get(leeftijdIdx).getText());
                    int minLengte      = parseMinWaarde(rule.getInputEntry().get(lengteIdx).getText());
                    // Begeleiding is vereist als de cel expliciet "true" bevat
                    boolean begeleiding = begeleidingIdx >= 0 &&
                            "true".equals(rule.getInputEntry().get(begeleidingIdx).getText().trim());
                    // Zwanger is verboden als de cel "false" bevat (toegang alleen als niet zwanger)
                    boolean zwangerVerboden = zwangerIdx >= 0 &&
                            "false".equals(rule.getInputEntry().get(zwangerIdx).getText().trim());
                    return new AttractieDetail(naam, minLeeftijd, minLengte, begeleiding, zwangerVerboden);
                })
                // Eén attractie kan meerdere "toegestaan"-regels hebben (bijv. met en zonder begeleiding)
                // We voegen die samen: laagste minimumleeftijd/-lengte, en vereisten gelden als één regel ze vereist
                .collect(Collectors.toMap(
                        AttractieDetail::naam,
                        a -> a,
                        (a, b) -> new AttractieDetail(
                                a.naam(),
                                Math.min(a.minLeeftijd(), b.minLeeftijd()),
                                Math.min(a.minLengte(), b.minLengte()),
                                a.begeleidingVereist() || b.begeleidingVereist(),
                                a.zwangerVerboden()    || b.zwangerVerboden()
                        )
                ))
                .values().stream()
                .sorted((a, b) -> a.naam().compareTo(b.naam()))
                .collect(Collectors.toList());
    }

    // Ontvangt de bezoekersgegevens en laat de rule engine bepalen of toegang wordt verleend
    @POST
    @Path("/toegang")
    @Consumes(MediaType.APPLICATION_JSON)
    public ToegangsResultaat controleer(ToegangsVerzoek verzoek) {
        // Een nieuw DMN-context aanmaken en de bezoekersgegevens erin zetten
        DMNContext context = dmnRuntime.newContext();
        context.set("attractie",   verzoek.attractie());
        context.set("leeftijd",    verzoek.leeftijd());
        context.set("lengte",      verzoek.lengte());
        // Null-check voor booleans: als het veld niet meegegeven is, standaard false gebruiken
        context.set("begeleiding", verzoek.begeleiding() != null ? verzoek.begeleiding() : false);
        context.set("zwanger",     verzoek.zwanger()    != null ? verzoek.zwanger()    : false);
        // De regel engine uitvoeren met de ingevulde context
        DMNResult result = dmnRuntime.evaluateAll(dmnModel, context);
        // Het resultaat ophalen — als er niets uitkomt, standaard weigeren
        var decisionResult = result.getDecisionResultByName("Toegangsbeslissing");
        String beslissing = (decisionResult != null && decisionResult.getResult() != null)
                ? (String) decisionResult.getResult()
                : "Toegang geweigerd";
        return new ToegangsResultaat(beslissing);
    }

    // Serveert het DMN-bestand zodat KIE Sandbox het kan ophalen via de "Regels bewerken" knop
    @GET
    @Path("/dmn")
    @Produces("application/xml")
    public Response getDmn() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("toegangscontrole.dmn");
        // 404 teruggeven als het bestand om de een of andere reden niet gevonden wordt
        if (is == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(is)
                .header("Content-Disposition", "inline; filename=\"toegangscontrole.dmn\"")
                .build();
    }

    // Haalt de minimumwaarde op uit een FEEL-expressie zoals ">= 12" → 12
    private int parseMinWaarde(String feelExpr) {
        String cleaned = feelExpr.replace(">=", "").replace("\"", "").trim();
        try { return Integer.parseInt(cleaned); } catch (NumberFormatException e) { return 0; }
    }

    // Verwijdert aanhalingstekens uit een DMN-tekstwaarde, bijv. "\"Achtbaan\"" → "Achtbaan"
    private String tekstZonderAanhalingstekens(String text) {
        return text.replace("\"", "").replace("&quot;", "").trim();
    }
}
