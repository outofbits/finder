package at.ac.tuwien.finder.service.unittest;

import at.ac.tuwien.finder.datamanagement.TripleStoreManager;
import at.ac.tuwien.finder.dto.Dto;
import at.ac.tuwien.finder.dto.IResourceIdentifier;
import at.ac.tuwien.finder.service.ServiceFactory;
import at.ac.tuwien.finder.service.exception.IRIInvalidException;
import at.ac.tuwien.finder.service.exception.IRIUnknownException;
import at.ac.tuwien.finder.service.exception.ResourceNotFoundException;
import at.ac.tuwien.finder.service.exception.ServiceException;
import at.ac.tuwien.finder.vocabulary.LOCN;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.model.util.RDFCollections;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * THis class shall test the service structure concerning spatial data.
 *
 * @author Kevin Haller
 */
public class SpatialServicesTest {

    private static IRI BASE = TripleStoreManager.BASE;
    private static IResourceIdentifier BASE_IRI;

    private static ValueFactory valueFactory = SimpleValueFactory.getInstance();

    public SpatialServicesTest() throws URISyntaxException {
        BASE_IRI = new IResourceIdentifier(BASE.stringValue());
    }

    /* Test data for the triple store */
    private static Model dbTestData;

    @BeforeClass
    public static void setUpClass() throws IOException {
        dbTestData = Rio.parse(
            SpatialServicesTest.class.getClassLoader().getResourceAsStream("dbTestDump.trig"), "",
            RDFFormat.TRIG);
    }

    private TripleStoreManager tripleStoreManager;
    private ServiceFactory serviceFactory;

    @Before
    public void setUp() throws Exception {
        tripleStoreManager = mock(TripleStoreManager.class);
        Repository repository = new SailRepository(new MemoryStore());
        repository.initialize();
        try (RepositoryConnection connection = repository.getConnection()) {
            connection.add(dbTestData);
        }
        when(tripleStoreManager.getConnection()).thenReturn(repository.getConnection());
        serviceFactory = new ServiceFactory(tripleStoreManager);
    }

    @Test
    public void get_building_with_id_ok()
        throws ServiceException, IRIUnknownException, IRIInvalidException {
        IRI buildingA = valueFactory.createIRI(BASE.stringValue(), "spatial/building/id/A");
        Model result =
            serviceFactory.getService(BASE_IRI, getPathScanner("spatial/building/id/A"), null)
                .execute().getModel();
        assertTrue(String.format("Resource <%s> must be part of the result.", buildingA.toString()),
            result.subjects().contains(buildingA));
        assertThat(String.format(
            "Resource <%s> has two rdfs:label objects (Central building@en, Hauptgebäude@de).",
            buildingA.toString()),
            result.filter(buildingA, RDFS.LABEL, null).objects().stream().map(Value::stringValue)
                .collect(Collectors.toList()),
            containsInAnyOrder("Central building", "Hauptgebäude"));
    }

    @Test
    public void get_tracts_of_building_with_id_ok()
        throws ServiceException, IRIInvalidException, IRIUnknownException {
        Dto resultDto = serviceFactory
            .getService(BASE_IRI, getPathScanner("spatial/building/id/DABC/buildingtracts"), null)
            .execute();
        List<Resource> resultValues = RDFCollections
            .asValues(resultDto.getModel(), valueFactory.createIRI(resultDto.getIRI().toString()),
                new LinkedList<>()).stream().filter(value -> value instanceof Resource)
            .map(value -> (Resource) value).collect(Collectors.toList());
        assertThat("The result must contain building tracts with the ids DA, DB, DC.", resultValues,
            hasItems(valueFactory.createIRI(BASE.stringValue(), "spatial/buildingtract/id/DA"),
                valueFactory.createIRI(BASE.stringValue(), "spatial/buildingtract/id/DB"),
                valueFactory.createIRI(BASE.stringValue(), "spatial/buildingtract/id/DC")));
        assertThat("The result model must contain only three building tracts.", resultValues,
            hasSize(3));
    }

    @Test(expected = ResourceNotFoundException.class)
    public void get_building_with_unknown_id_throws_ResourceNotFoundException()
        throws ServiceException, IRIUnknownException, IRIInvalidException {
        serviceFactory.getService(BASE_IRI, getPathScanner("spatial/building/id/ABC"), null)
            .execute();
    }

    @Test
    public void get_buildingtract_with_id_ok()
        throws ServiceException, IRIInvalidException, IRIUnknownException {
        IRI buildingTractAA =
            valueFactory.createIRI(BASE.stringValue(), "spatial/buildingtract/id/AA");
        Model result =
            serviceFactory.getService(BASE_IRI, getPathScanner("spatial/buildingtract/id/AA"), null)
                .execute().getModel();
        assertTrue(
            String.format("Resource <%s> must be part of the result.", buildingTractAA.toString()),
            result.subjects().contains(buildingTractAA));
        assertThat(String
                .format("Resource <%s> has the rdfs:label 'AA Haupttrakt'", buildingTractAA.toString()),
            result.filter(buildingTractAA, RDFS.LABEL, null).objects().iterator().next()
                .stringValue(), is("AA Haupttrakt"));
    }

    @Test(expected = ResourceNotFoundException.class)
    public void get_buildingtract_with_unknown_id_throws_ResourceNotFoundException()
        throws IRIInvalidException, IRIUnknownException, ServiceException {
        serviceFactory.getService(BASE_IRI, getPathScanner("spatial/buildingtract/id/ABCD"), null)
            .execute();
    }

    @Test
    public void get_floor_with_id_ok()
        throws ServiceException, IRIUnknownException, IRIInvalidException {
        IRI floorAA03 = valueFactory.createIRI(BASE.stringValue(), "spatial/floor/id/AA03");
        Model result =
            serviceFactory.getService(BASE_IRI, getPathScanner("spatial/floor/id/AA03"), null)
                .execute().getModel();
        assertTrue(String.format("Resource <%s> must be part of the result.", floorAA03.toString()),
            result.subjects().contains(floorAA03));
        assertThat(String.format("Resource <%s> has rdfs:label '03'.", floorAA03.toString()),
            result.filter(floorAA03, RDFS.LABEL, null).objects().iterator().next().stringValue(),
            is("03"));
    }

    @Test(expected = ResourceNotFoundException.class)
    public void get_floor_with_unknown_id_throws_ResourceNotFoundException()
        throws IRIInvalidException, IRIUnknownException, ServiceException {
        serviceFactory.getService(BASE_IRI, getPathScanner("spatial/floor/id/ABCD01"), null)
            .execute();
    }

    @Test
    public void get_address_with_id_ok()
        throws IRIInvalidException, IRIUnknownException, ServiceException {
        IRI addressKarlsplatz = valueFactory.createIRI(BASE.stringValue(),
            "spatial/address/id/AT1040-1c56fcbcb8725edda11e2c76a1d21c77-13");
        Model result = serviceFactory.getService(BASE_IRI,
            getPathScanner("spatial/address/id/AT1040-1c56fcbcb8725edda11e2c76a1d21c77-13"), null)
            .execute().getModel();
        assertTrue(String
                .format("Resource <%s> must be part of the result.", addressKarlsplatz.toString()),
            result.subjects().contains(addressKarlsplatz));
        assertThat(
            String.format("Resource <%s> has locn:postCode '1040'.", addressKarlsplatz.toString()),
            Models.objectLiteral(result.filter(addressKarlsplatz, LOCN.fullAddress, null)).get()
                .stringValue(), is("Karlsplatz 13, 1040 Wien, Österreich"));
        assertThat(
            String.format("Resource <%s> has locn:postCode '1040'.", addressKarlsplatz.toString()),
            Models.objectLiteral(result.filter(addressKarlsplatz, LOCN.postCode, null)).get()
                .stringValue(), is("1040"));
        assertThat(
            String.format("Resource <%s> has locn:poBox '13'.", addressKarlsplatz.toString()),
            Models.objectLiteral(result.filter(addressKarlsplatz, LOCN.locatorDesignator, null))
                .get().stringValue(), is("13"));
    }

    @Test(expected = ResourceNotFoundException.class)
    public void get_address_with_unknown_id_throws_IRIUnknownException()
        throws ServiceException, IRIUnknownException, IRIInvalidException {
        serviceFactory.getService(BASE_IRI,
            getPathScanner("spatial/address/id/DE1100-1c56fcbcb8725edda11e2c76a1d21c77-na"), null)
            .execute();
    }

    private static Scanner getPathScanner(String path) {
        Scanner pathScanner = new Scanner(path);
        pathScanner.useDelimiter("/");
        return pathScanner;
    }

}
