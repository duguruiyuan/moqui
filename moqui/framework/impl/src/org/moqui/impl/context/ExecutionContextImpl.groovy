/*
 * This Work is in the public domain and is provided on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE,
 * NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * You are solely responsible for determining the appropriateness of using
 * this Work and assume any risks associated with your use of this Work.
 *
 * This Work includes contributions authored by David E. Jones, not as a
 * "work for hire", who hereby disclaims any copyright to the same.
 */
package org.moqui.impl.context

import org.moqui.context.ExecutionContext
import org.moqui.context.UserFacade
import org.moqui.context.MessageFacade
import org.moqui.context.L10nFacade
import org.moqui.context.ResourceFacade
import org.moqui.context.LoggerFacade
import org.moqui.context.CacheFacade
import org.moqui.context.TransactionFacade
import org.moqui.entity.EntityFacade
import org.moqui.service.ServiceFacade
import org.moqui.context.ScreenFacade
import org.moqui.context.ArtifactExecutionFacade

class ExecutionContextImpl implements ExecutionContext {

    ExecutionContextFactoryImpl ecfi;

    ExecutionContextImpl(ExecutionContextFactoryImpl ecfi) {
        this.ecfi = ecfi;
    }

    /** @see org.moqui.context.ExecutionContext#getContext() */
    Map<String, Object> getContext() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContext#getContextRoot() */
    Map<String, Object> getContextRoot() {
        return null;  // TODO: implement this
    }
    
    /** @see org.moqui.context.ExecutionContext#getTenantId() */
    String getTenantId() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContext#getUser() */
    UserFacade getUser() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContext#getMessage() */
    MessageFacade getMessage() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContext#getL10n() */
    L10nFacade getL10n() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContext#getArtifactExecution() */
    ArtifactExecutionFacade getArtifactExecution() {
        return null;  // TODO: implement this
    }

    /** @see org.moqui.context.ExecutionContext#getResource() */
    ResourceFacade getResource() {
        return this.ecfi.getResourceFacade();
    }

    /** @see org.moqui.context.ExecutionContext#getLogger() */
    LoggerFacade getLogger() {
        return this.ecfi.getLoggerFacade();
    }

    /** @see org.moqui.context.ExecutionContext#getCache() */
    CacheFacade getCache() {
        return this.ecfi.getCacheFacade();
    }

    /** @see org.moqui.context.ExecutionContext#getTransaction() */
    TransactionFacade getTransaction() {
        return this.ecfi.getTransactionFacade();
    }

    /** @see org.moqui.context.ExecutionContext#getEntity() */
    EntityFacade getEntity() {
        return this.ecfi.getEntityFacade();
    }

    /** @see org.moqui.context.ExecutionContext#getService() */
    ServiceFacade getService() {
        return this.ecfi.getServiceFacade();
    }

    /** @see org.moqui.context.ExecutionContext#getScreen() */
    ScreenFacade getScreen() {
        return this.ecfi.getScreenFacade();
    }
}