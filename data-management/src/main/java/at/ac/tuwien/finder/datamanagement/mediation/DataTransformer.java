package at.ac.tuwien.finder.datamanagement.mediation;

import at.ac.tuwien.finder.datamanagement.mediation.exception.DataTransformationException;
import org.eclipse.rdf4j.model.Model;

/**
 * Instances of this interface represents a transformer that converts the (possibly messy) data
 * of a {@link DataAcquirer} into linked data (rdf). The data transformer corresponds to the
 * acquirer.
 *
 * @param <I> the type of the data, which shall be transformed.
 * @author Kevin Haller
 */
public interface DataTransformer<I> extends AutoCloseable {

    /**
     * Transforms the given data into linked data (rdf).
     *
     * @param data the data, which shall be transformed into linked data.
     * @return the result of the transformation.
     * @throws DataTransformationException if the transformation was not successful.
     */
    Model transform(I data) throws DataTransformationException;

}
