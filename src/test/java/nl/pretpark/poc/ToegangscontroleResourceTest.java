package nl.pretpark.poc;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

@QuarkusTest
class ToegangscontroleResourceTest {

    // ── Achtbaan ──────────────────────────────────────────────────────────────

    @Test
    void achtbaanToegstaanBijGeldigeGegevens() {
        given().contentType(ContentType.JSON)
                .body("{\"attractie\":\"Achtbaan\",\"leeftijd\":15,\"lengte\":145,\"zwanger\":false,\"begeleiding\":false}")
                .when().post("/toegang")
                .then().statusCode(200)
                .body("beslissing", equalTo("Toegang toegestaan"));
    }

    @Test
    void achtbaanGeweigerdBijTeJong() {
        given().contentType(ContentType.JSON)
                .body("{\"attractie\":\"Achtbaan\",\"leeftijd\":10,\"lengte\":145,\"zwanger\":false,\"begeleiding\":false}")
                .when().post("/toegang")
                .then().statusCode(200)
                .body("beslissing", equalTo("Toegang geweigerd"));
    }

    @Test
    void achtbaanGeweigerdBijTeKlein() {
        given().contentType(ContentType.JSON)
                .body("{\"attractie\":\"Achtbaan\",\"leeftijd\":15,\"lengte\":130,\"zwanger\":false,\"begeleiding\":false}")
                .when().post("/toegang")
                .then().statusCode(200)
                .body("beslissing", equalTo("Toegang geweigerd"));
    }

    @Test
    void achtbaanGeweigerdBijZwanger() {
        given().contentType(ContentType.JSON)
                .body("{\"attractie\":\"Achtbaan\",\"leeftijd\":25,\"lengte\":165,\"zwanger\":true,\"begeleiding\":false}")
                .when().post("/toegang")
                .then().statusCode(200)
                .body("beslissing", equalTo("Toegang geweigerd"));
    }

    @Test
    void achtbaanToegstaanOpExacteGrenswaarden() {
        given().contentType(ContentType.JSON)
                .body("{\"attractie\":\"Achtbaan\",\"leeftijd\":12,\"lengte\":140,\"zwanger\":false,\"begeleiding\":false}")
                .when().post("/toegang")
                .then().statusCode(200)
                .body("beslissing", equalTo("Toegang toegestaan"));
    }

    // ── Kinder Achtbaan ───────────────────────────────────────────────────────

    @Test
    void kinderAchtbaanToegstaanZelfstandig() {
        // 8 jaar of ouder mag zelfstandig, begeleiding niet vereist
        given().contentType(ContentType.JSON)
                .body("{\"attractie\":\"Kinder Achtbaan\",\"leeftijd\":8,\"lengte\":105,\"zwanger\":false,\"begeleiding\":false}")
                .when().post("/toegang")
                .then().statusCode(200)
                .body("beslissing", equalTo("Toegang toegestaan"));
    }

    @Test
    void kinderAchtbaanToegstaanMetBegeleiding() {
        // 4 t/m 7 jaar mag met begeleiding van een volwassene
        given().contentType(ContentType.JSON)
                .body("{\"attractie\":\"Kinder Achtbaan\",\"leeftijd\":5,\"lengte\":105,\"zwanger\":false,\"begeleiding\":true}")
                .when().post("/toegang")
                .then().statusCode(200)
                .body("beslissing", equalTo("Toegang toegestaan"));
    }

    @Test
    void kinderAchtbaanGeweigerdZonderBegeleiding() {
        // 4 t/m 7 jaar zonder begeleiding wordt geweigerd
        given().contentType(ContentType.JSON)
                .body("{\"attractie\":\"Kinder Achtbaan\",\"leeftijd\":5,\"lengte\":105,\"zwanger\":false,\"begeleiding\":false}")
                .when().post("/toegang")
                .then().statusCode(200)
                .body("beslissing", equalTo("Toegang geweigerd"));
    }

    @Test
    void kinderAchtbaanGeweigerdTeJong() {
        // Onder de 4 jaar geen toegang, ook niet met begeleiding
        given().contentType(ContentType.JSON)
                .body("{\"attractie\":\"Kinder Achtbaan\",\"leeftijd\":3,\"lengte\":105,\"zwanger\":false,\"begeleiding\":true}")
                .when().post("/toegang")
                .then().statusCode(200)
                .body("beslissing", equalTo("Toegang geweigerd"));
    }

    // ── Wildwaterbaan ─────────────────────────────────────────────────────────

    @Test
    void wildwaterToegstaan() {
        given().contentType(ContentType.JSON)
                .body("{\"attractie\":\"Wildwaterbaan\",\"leeftijd\":10,\"lengte\":125,\"zwanger\":false,\"begeleiding\":false}")
                .when().post("/toegang")
                .then().statusCode(200)
                .body("beslissing", equalTo("Toegang toegestaan"));
    }

    @Test
    void wildwaterGeweigerdTeJong() {
        given().contentType(ContentType.JSON)
                .body("{\"attractie\":\"Wildwaterbaan\",\"leeftijd\":6,\"lengte\":125,\"zwanger\":false,\"begeleiding\":false}")
                .when().post("/toegang")
                .then().statusCode(200)
                .body("beslissing", equalTo("Toegang geweigerd"));
    }

    // ── Vrije val ─────────────────────────────────────────────────────────────

    @Test
    void vrijeValToegstaan() {
        given().contentType(ContentType.JSON)
                .body("{\"attractie\":\"Vrije val\",\"leeftijd\":18,\"lengte\":155,\"zwanger\":false,\"begeleiding\":false}")
                .when().post("/toegang")
                .then().statusCode(200)
                .body("beslissing", equalTo("Toegang toegestaan"));
    }

    @Test
    void vrijeValGeweigerdTeJong() {
        given().contentType(ContentType.JSON)
                .body("{\"attractie\":\"Vrije val\",\"leeftijd\":14,\"lengte\":155,\"zwanger\":false,\"begeleiding\":false}")
                .when().post("/toegang")
                .then().statusCode(200)
                .body("beslissing", equalTo("Toegang geweigerd"));
    }

    // ── Attracties endpoint ───────────────────────────────────────────────────

    @Test
    void attractiesDetailsBevatAlleVereisten() {
        given().when().get("/attracties/details")
                .then().statusCode(200)
                .body("naam", hasItems("Achtbaan", "Wildwaterbaan", "Vrije val", "Kinder Achtbaan"))
                .body("find { it.naam == 'Achtbaan' }.zwangerVerboden", equalTo(true))
                .body("find { it.naam == 'Kinder Achtbaan' }.begeleidingVereist", equalTo(true));
    }
}
