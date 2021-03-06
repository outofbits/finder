package at.ac.tuwien.finder.datamanagement.mediation.organizational;

import at.ac.tuwien.finder.datamanagement.mediation.DataTransformer;
import at.ac.tuwien.finder.datamanagement.mediation.exception.DataAcquireException;
import at.ac.tuwien.finder.datamanagement.mediation.exception.DataTransformationException;
import at.ac.tuwien.finder.datamanagement.mediation.transformer.DOMXSLTransformer;
import at.ac.tuwien.finder.datamanagement.util.TISSApiGetRequest;
import at.ac.tuwien.finder.datamanagement.util.exception.TISSApiRequestFailedException;
import at.ac.tuwien.finder.datamanagement.util.TaskManager;
import org.apache.commons.csv.CSVFormat;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.stream.Collectors;

/**
 * This is an implementation of {@link  TISSPersonCrawler} that uses the TISS Rest API for gathering
 * the information of the person(s).
 *
 * @author Kevin Haller
 */
public class RestTISSPersonCrawler
    implements TISSPersonCrawler, OrganizationalDataAcquirer<Document> {

    private static final Logger logger = LoggerFactory.getLogger(RestTISSPersonCrawler.class);

    private static final String PERSON_API_CALL = "person/oid/";

    private static final String TISS_PERSON_XSLT_STYLESHEET =
        "organizational/tissPersonsRdfTransform.xsl";

    private TaskManager taskManager = TaskManager.getInstance();

    private DocumentBuilderFactory documentFactory;
    private HttpClientBuilder httpClientBuilder;

    /**
     * Creates an instance of {@link RestTISSPersonCrawler}.
     */
    public RestTISSPersonCrawler() {
        httpClientBuilder = HttpClientBuilder.create();
        documentFactory = DocumentBuilderFactory.newInstance();
    }

    @Override
    public Document getInformationOfPerson(String personOId) throws DataAcquireException {
        logger.debug("Get information of the person with the id {}.", personOId);
        try {
            return TISSApiGetRequest.call(httpClientBuilder, PERSON_API_CALL + personOId)
                .xmlDocument();
        } catch (TISSApiRequestFailedException e) {
            logger.error("Crawling failed for the person {}. {}", personOId, e);
            throw new DataAcquireException(e);
        }
    }

    @Override
    public Document getInformationOfPersons(Collection<String> personOIdList)
        throws DataAcquireException {
        CompletionService<Document> completionService =
            new ExecutorCompletionService<>(taskManager.threadPool());
        for (String oid : personOIdList) {
            completionService.submit(() -> getInformationOfPerson(oid));
        }
        try {
            Document personsDocument = documentFactory.newDocumentBuilder().newDocument();
            Element personsRootElement = personsDocument.createElement("persons");
            for (int i = 0; i < personOIdList.size(); i++) {
                try {
                    Document responsePersonDocument = completionService.take().get();
                    NodeList personNodeList =
                        responsePersonDocument.getElementsByTagName("tuvienna");
                    if (personNodeList.getLength() == 1) {
                        personsRootElement
                            .appendChild(personsDocument.importNode(personNodeList.item(0), true));
                    }
                } catch (InterruptedException | ExecutionException e) {
                    logger.debug("{}", e);
                    continue;
                }
            }
            personsDocument.appendChild(personsRootElement);
            return personsDocument;
        } catch (ParserConfigurationException e) {
            logger.debug("{}", e);
            throw new DataAcquireException(e);
        }
    }

    @Override
    public DataTransformer<Document> transformer() throws DataTransformationException {
        return new DOMXSLTransformer(RestTISSPersonCrawler.class.getClassLoader()
            .getResourceAsStream(TISS_PERSON_XSLT_STYLESHEET), RDFFormat.RDFXML);
    }

    @Override
    public Document acquire() throws DataAcquireException {
        try (BufferedReader oidCsvReader = new BufferedReader(new InputStreamReader(
            RestTISSPersonCrawler.class.getClassLoader()
                .getResourceAsStream("organizational/orgunitOIds.csv")))) {
            Map<String, String> includePersonsMap = new HashMap<>();
            includePersonsMap.put("persons", "true");
            try (TISSOrganizationCrawler orgCrawler = new RestTISSOrganizationCrawler(
                includePersonsMap)) {
                Document organizationInformationDocument = orgCrawler
                    .getOrganizationInformationByIds(
                        CSVFormat.EXCEL.parse(oidCsvReader).getRecords().stream()
                            .map(record -> record.get(0).trim().replaceAll("^\"|\"$", ""))
                            .collect(Collectors.toList()));
                Set<String> allPersonOids = new HashSet<>();
                NodeList personNodeList =
                    organizationInformationDocument.getElementsByTagName("person");
                for (int i = 0; i < personNodeList.getLength(); i++) {
                    Element personElement = (Element) personNodeList.item(i);
                    allPersonOids.add(personElement.getAttribute("oid"));
                }
                return getInformationOfPersons(allPersonOids);
            } catch (Exception e) {
                logger.error("{}", e);
            }
            return null;
        } catch (IOException e) {
            throw new DataAcquireException(
                "The CSV file containing the organization OIDs cannot be accessed.", e);
        }
    }

    @Override
    public void close() throws Exception {
        taskManager.close();
    }

}
