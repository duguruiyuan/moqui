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
package org.moqui.impl.service.runner

import org.moqui.BaseException
import org.moqui.entity.EntityList
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityValueNotFoundException
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.entity.EntityValueBase
import org.moqui.impl.service.ServiceDefinition
import org.moqui.impl.service.ServiceFacadeImpl
import org.moqui.impl.service.ServiceRunner
import org.moqui.service.ServiceException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Timestamp

public class EntityAutoServiceRunner implements ServiceRunner {
    protected final static Logger logger = LoggerFactory.getLogger(EntityAutoServiceRunner.class)

    final static Set<String> verbSet = new TreeSet(["create", "update", "delete", "store"])
    protected ServiceFacadeImpl sfi = null

    EntityAutoServiceRunner() {}

    ServiceRunner init(ServiceFacadeImpl sfi) { this.sfi = sfi; return this }

    // TODO: add update-expire and delete-expire entity-auto service verbs for entities with from/thru dates
    // TODO: add find (using search input parameters) and find-one (using literal PK, or as many PK fields as are passed on) entity-auto verbs
    Map<String, Object> runService(ServiceDefinition sd, Map<String, Object> parameters) {
        // check the verb and noun
        if (!sd.verb || !verbSet.contains(sd.verb))
            throw new ServiceException("In service [${sd.serviceName}] the verb must be one of ${verbSet} for entity-auto type services.")
        if (!sd.noun)  throw new ServiceException("In service [${sd.serviceName}] you must specify a noun for entity-auto engine")

        EntityDefinition ed = sfi.ecfi.entityFacade.getEntityDefinition(sd.noun)
        if (!ed) throw new ServiceException("In service [${sd.serviceName}] the specified noun [${sd.noun}] is not a valid entity name")

        Map<String, Object> result = new HashMap()

        try {
            boolean allPksInOnly = true
            for (String pkFieldName in ed.getPkFieldNames()) {
                if (!sd.getInParameter(pkFieldName) || sd.getOutParameter(pkFieldName)) { allPksInOnly = false; break }
            }

            if ("create" == sd.verb) {
                createEntity(sfi, ed, parameters, result, sd.getOutParameterNames())
            } else if ("update" == sd.verb) {
                /* <auto-attributes include="pk" mode="IN" optional="false"/> */
                if (!allPksInOnly) throw new ServiceException("In entity-auto type service [${sd.serviceName}] with update noun, not all pk fields have the mode IN")
                updateEntity(sfi, ed, parameters, result, sd.getOutParameterNames(), null)
            } else if ("delete" == sd.verb) {
                /* <auto-attributes include="pk" mode="IN" optional="false"/> */
                if (!allPksInOnly) throw new ServiceException("In entity-auto type service [${sd.serviceName}] with delete noun, not all pk fields have the mode IN")
                deleteEntity(sfi, ed, parameters)
            } else if ("store" == sd.verb) {
                storeEntity(sfi, ed, parameters, result, sd.getOutParameterNames())
            } else if ("update-expire" == sd.verb) {
                // TODO
            } else if ("delete-expire" == sd.verb) {
                // TODO
            } else if ("find" == sd.verb) {
                // TODO
            } else if ("find-one" == sd.verb) {
                // TODO
            }
        } catch (BaseException e) {
            throw new ServiceException("Error doing entity-auto operation for entity [${ed.fullEntityName}] in service [${sd.serviceName}]", e)
        }

        return result
    }

    protected static void checkFromDate(EntityDefinition ed, Map<String, Object> parameters,
                              Map<String, Object> result, ExecutionContextFactoryImpl ecfi) {
        List<String> pkFieldNames = ed.getPkFieldNames()

        // always make fromDate optional, whether or not part of the pk; do this before the allPksIn check
        if (pkFieldNames.contains("fromDate") && parameters.get("fromDate") == null) {
            Timestamp fromDate = ecfi.getExecutionContext().getUser().getNowTimestamp()
            parameters.put("fromDate", fromDate)
            result.put("fromDate", fromDate)
            // logger.info("Set fromDate field to default [${parameters.fromDate}]")
        }
    }

    protected static boolean checkAllPkFields(EntityDefinition ed, Map<String, Object> parameters, Map<String, Object> tempResult,
                                    EntityValue newEntityValue, Set<String> outParamNames) {
        List<String> pkFieldNames = ed.getPkFieldNames()

        // see if all PK fields were passed in
        boolean allPksIn = true
        for (String pkFieldName in pkFieldNames) if (!parameters.get(pkFieldName)) { allPksIn = false; break }
        boolean isSinglePk = pkFieldNames.size() == 1
        boolean isDoublePk = pkFieldNames.size() == 2

        // logger.info("allPksIn=${allPksIn}, isSinglePk=${isSinglePk}, isDoublePk=${isDoublePk}")

        if (isSinglePk) {
            /* **** primary sequenced primary key **** */
            /* **** primary sequenced key with optional override passed in **** */
            String singlePkParamName = pkFieldNames.get(0)
            Node singlePkField = ed.getFieldNode(singlePkParamName)

            Object pkValue = parameters.get(singlePkField."@name")
            if (pkValue) {
                newEntityValue.set((String) singlePkField."@name", pkValue)
            } else {
                // if it has a default value don't sequence the PK
                if (!singlePkField."@default") {
                    newEntityValue.setSequencedIdPrimary()
                    pkValue = newEntityValue.get(singlePkField."@name")
                }
            }
            if (outParamNames == null || outParamNames.contains(singlePkParamName))
                tempResult.put(singlePkParamName, pkValue)
        } else if (isDoublePk && !allPksIn) {
            /* **** secondary sequenced primary key **** */
            // don't do it this way, currently only supports second pk fields: String doublePkSecondaryName = parameters.get(pkFieldNames.get(0)) ? pkFieldNames.get(1) : pkFieldNames.get(0)
            String doublePkSecondaryName = pkFieldNames.get(1)
            newEntityValue.setFields(parameters, true, null, true)
            // if it has a default value don't sequence the PK
            Node doublePkSecondaryNode = ed.getFieldNode(doublePkSecondaryName)
            if (!doublePkSecondaryNode."@default") {
                newEntityValue.setSequencedIdSecondary()
                if (outParamNames == null || outParamNames.contains(doublePkSecondaryName))
                    tempResult.put(doublePkSecondaryName, newEntityValue.get(doublePkSecondaryName))
            }
        } else if (allPksIn) {
            /* **** plain specified primary key **** */
            newEntityValue.setFields(parameters, true, null, true)
        } else {
            logger.error("Entity [${ed.getFullEntityName()}] auto create pk fields ${pkFieldNames} incomplete: ${parameters}")
            throw new ServiceException("In entity-auto create service for entity [${ed.fullEntityName}]: " +
                    "could not find a valid combination of primary key settings to do a create operation; options include: " +
                    "1. a single entity primary-key field for primary auto-sequencing with or without matching in-parameter, and with or without matching out-parameter for the possibly sequenced value, " +
                    "2. a 2-part entity primary-key with one part passed in as an in-parameter (existing primary pk value) and with or without the other part defined as an out-parameter (the secodnary pk to sub-sequence), " +
                    "3. all entity pk fields are passed into the service");
        }

        // logger.info("In auto createEntity allPksIn [${allPksIn}] isSinglePk [${isSinglePk}] isDoublePk [${isDoublePk}] newEntityValue final [${newEntityValue}]")

        return allPksIn
    }

    static void createEntity(ServiceFacadeImpl sfi, EntityDefinition ed, Map<String, Object> parameters,
                                    Map<String, Object> result, Set<String> outParamNames) {
        ExecutionContextFactoryImpl ecfi = sfi.getEcfi()
        createRecursive(ecfi, ed, parameters, result, outParamNames)
    }

    static void createRecursive(ExecutionContextFactoryImpl ecfi, EntityDefinition ed, Map<String, Object> parameters,
                                Map<String, Object> result, Set<String> outParamNames) {
        EntityValue newEntityValue = ecfi.getEntityFacade().makeValue(ed.getFullEntityName())

        checkFromDate(ed, parameters, result, ecfi)

        Map<String, Object> tempResult = [:]
        checkAllPkFields(ed, parameters, tempResult, newEntityValue, outParamNames)

        newEntityValue.setFields(parameters, true, null, false)
        newEntityValue.create()

        Map pkMap = newEntityValue.getPrimaryKeys()

        // if a PK field has a @default get it and return it
        List<Node> pkNodes = ed.getFieldNodes(true, false, false)
        for (Node pkNode in pkNodes) if (pkNode."@default")
            tempResult.put((String) pkNode."@name", newEntityValue.get((String) pkNode."@name"))

        // check parameters Map for relationships
        for (EntityDefinition.RelationshipInfo relInfo in ed.getRelationshipsInfo(false)) {
            Object relParmObj = parameters.get(relInfo.shortAlias)
            String relKey = null
            if (relParmObj) {
                relKey = relInfo.shortAlias
            } else {
                relParmObj = parameters.get(relInfo.relationshipName)
                if (relParmObj) relKey = relInfo.relationshipName
            }
            if (relParmObj) {
                if (relParmObj instanceof Map) {
                    Map relResults = [:]
                    // add in all of the main entity's primary key fields, this is necessary for auto-generated, and to
                    //     allow them to be left out of related records
                    relParmObj.putAll(pkMap)
                    createRecursive(ecfi, relInfo.relatedEd, relParmObj, relResults, null)
                    tempResult.put(relKey, relResults)
                } else if (relParmObj instanceof List) {
                    List relResultList = []
                    for (Object relParmEntry in relParmObj) {
                        Map relResults = [:]
                        if (relParmEntry instanceof Map) {
                            relParmEntry.putAll(pkMap)
                            createRecursive(ecfi, relInfo.relatedEd, relParmEntry, relResults, null)
                        } else {
                            logger.warn("In entity auto create for entity ${ed.getFullEntityName()} found list for relationship ${relKey} with a non-Map entry: ${relParmEntry}")
                        }
                        relResultList.add(relResults)

                    }
                    tempResult.put(relKey, relResultList)
                }
            }
        }

        result.putAll(tempResult)
    }

    /* This should only be called if statusId is a field of the entity and lookedUpValue != null */
    protected static void checkStatus(EntityDefinition ed, Map<String, Object> parameters, Map<String, Object> result,
                            Set<String> outParamNames, EntityValue lookedUpValue, ExecutionContextFactoryImpl ecfi) {
        if (!parameters.containsKey("statusId")) return

        // populate the oldStatusId out if there is a service parameter for it, and before we do the set non-pk fields
        if (outParamNames == null || outParamNames.contains("oldStatusId")) {
            result.put("oldStatusId", lookedUpValue.get("statusId"))
        }
        if (outParamNames == null || outParamNames.contains("statusChanged")) {
            result.put("statusChanged", !(lookedUpValue.get("statusId") == parameters.get("statusId")))
            // logger.warn("========= oldStatusId=${result.oldStatusId}, statusChanged=${result.statusChanged}, lookedUpValue.statusId=${lookedUpValue.statusId}, parameters.statusId=${parameters.statusId}, lookedUpValue=${lookedUpValue}")
        }

        // do the StatusValidChange check
        String parameterStatusId = (String) parameters.get("statusId")
        if (parameterStatusId) {
            String lookedUpStatusId = (String) lookedUpValue.get("statusId")
            if (lookedUpStatusId && !parameterStatusId.equals(lookedUpStatusId)) {
                // there was an old status, and in this call we are trying to change it, so do the StatusFlowTransition check
                // NOTE that we are using a cached list from a common pattern so it should generally be there instead of a count that wouldn't
                EntityList statusFlowTransitionList = ecfi.getEntityFacade().find("moqui.basic.StatusFlowTransition")
                        .condition(["statusId":lookedUpStatusId, "toStatusId":parameterStatusId]).useCache(true).list()
                if (!statusFlowTransitionList) {
                    // uh-oh, no valid change...
                    throw new ServiceException("In entity-auto update service for entity [${ed.fullEntityName}] no status change was found going from status [${lookedUpStatusId}] to status [${parameterStatusId}]")
                }
            }
        }

        // NOTE: nothing here to maintain the status history, that should be done with a custom service called by SECA rule or with audit log on field
    }

    static void updateEntity(ServiceFacadeImpl sfi, EntityDefinition ed, Map<String, Object> parameters,
                                    Map<String, Object> result, Set<String> outParamNames, EntityValue preLookedUpValue) {
        ExecutionContextFactoryImpl ecfi = sfi.getEcfi()
        EntityValue lookedUpValue = preLookedUpValue ?:
                ecfi.getEntityFacade().makeValue(ed.getFullEntityName()).setFields(parameters, true, null, true)
        // this is much slower, and we don't need to do the query: sfi.getEcfi().getEntityFacade().find(ed.entityName).condition(parameters).useCache(false).forUpdate(true).one()
        if (lookedUpValue == null) {
            throw new EntityValueNotFoundException("In entity-auto update service for entity [${ed.fullEntityName}] value not found, cannot update; using parameters [${parameters}]")
        }

        if (parameters.containsKey("statusId") && ed.isField("statusId")) {
            // do the actual query so we'll have the current statusId
            lookedUpValue = preLookedUpValue ?: ecfi.getEntityFacade().find(ed.getFullEntityName())
                    .condition(parameters).useCache(false).forUpdate(true).one()
            if (lookedUpValue == null) {
                throw new EntityValueNotFoundException("In entity-auto update service for entity [${ed.fullEntityName}] value not found, cannot update; using parameters [${parameters}]")
            }

            checkStatus(ed, parameters, result, outParamNames, lookedUpValue, ecfi)
        }

        lookedUpValue.setFields(parameters, true, null, false)
        // logger.info("In auto updateEntity lookedUpValue final [${lookedUpValue}] for parameters [${parameters}]")
        lookedUpValue.update()

        storeRelated(ecfi, (EntityValueBase) lookedUpValue, parameters, result)
    }

    static void deleteEntity(ServiceFacadeImpl sfi, EntityDefinition ed, Map<String, Object> parameters) {
        EntityValue ev = sfi.getEcfi().getEntityFacade().makeValue(ed.getFullEntityName())
                .setFields(parameters, true, null, true)
        ev.delete()
    }

    /** Does a create if record does not exist, or update if it does. */
    static void storeEntity(ServiceFacadeImpl sfi, EntityDefinition ed, Map<String, Object> parameters,
                                   Map<String, Object> result, Set<String> outParamNames) {
        ExecutionContextFactoryImpl ecfi = sfi.getEcfi()
        storeRecursive(ecfi, ed, parameters, result, outParamNames)
    }

    static void storeRecursive(ExecutionContextFactoryImpl ecfi, EntityDefinition ed, Map<String, Object> parameters,
                               Map<String, Object> result, Set<String> outParamNames) {
        EntityValue newEntityValue = ecfi.getEntityFacade().makeValue(ed.getFullEntityName())

        checkFromDate(ed, parameters, result, ecfi)

        Map<String, Object> tempResult = [:]
        boolean allPksIn = checkAllPkFields(ed, parameters, tempResult, newEntityValue, outParamNames)
        result.putAll(tempResult)

        if (!allPksIn) {
            // we had to fill some stuff in, so do a create
            newEntityValue.setFields(parameters, true, null, false)
            newEntityValue.create()
            storeRelated(ecfi, (EntityValueBase) newEntityValue, parameters, result)
            return
        }

        EntityValue lookedUpValue = null
        if (parameters.containsKey("statusId") && ed.isField("statusId")) {
            // do the actual query so we'll have the current statusId
            lookedUpValue = ecfi.getEntityFacade().find(ed.getFullEntityName())
                    .condition(newEntityValue).useCache(false).forUpdate(true).one()
            if (lookedUpValue != null) {
                checkStatus(ed, parameters, result, outParamNames, lookedUpValue, ecfi)
            } else {
                // no lookedUpValue at this point? doesn't exist so create
                newEntityValue.setFields(parameters, true, null, false)
                newEntityValue.create()
                storeRelated(ecfi, (EntityValueBase) newEntityValue, parameters, result)
                return
            }
        }

        if (lookedUpValue == null) lookedUpValue = newEntityValue
        lookedUpValue.setFields(parameters, true, null, false)
        // logger.info("In auto updateEntity lookedUpValue final [${lookedUpValue}] for parameters [${parameters}]")
        lookedUpValue.store()

        storeRelated(ecfi, (EntityValueBase) lookedUpValue, parameters, result)
    }

    static void storeRelated(ExecutionContextFactoryImpl ecfi, EntityValueBase parentValue, Map<String, Object> parameters,
                             Map<String, Object> result) {
        EntityDefinition ed = parentValue.getEntityDefinition()
        Map pkMap = parentValue.getPrimaryKeys()

        // check parameters Map for relationships
        for (EntityDefinition.RelationshipInfo relInfo in ed.getRelationshipsInfo(false)) {
            Object relParmObj = parameters.get(relInfo.shortAlias)
            String relKey = null
            if (relParmObj) {
                relKey = relInfo.shortAlias
            } else {
                relParmObj = parameters.get(relInfo.relationshipName)
                if (relParmObj) relKey = relInfo.relationshipName
            }
            if (relParmObj) {
                if (relParmObj instanceof Map) {
                    Map relResults = [:]
                    // add in all of the main entity's primary key fields, this is necessary for auto-generated, and to
                    //     allow them to be left out of related records
                    relParmObj.putAll(pkMap)
                    storeRecursive(ecfi, relInfo.relatedEd, relParmObj, relResults, null)
                    result.put(relKey, relResults)
                } else if (relParmObj instanceof List) {
                    List relResultList = []
                    for (Object relParmEntry in relParmObj) {
                        Map relResults = [:]
                        if (relParmEntry instanceof Map) {
                            relParmEntry.putAll(pkMap)
                            storeRecursive(ecfi, relInfo.relatedEd, relParmEntry, relResults, null)
                        } else {
                            logger.warn("In entity auto create for entity ${ed.getFullEntityName()} found list for relationship ${relKey} with a non-Map entry: ${relParmEntry}")
                        }
                        relResultList.add(relResults)

                    }
                    result.put(relKey, relResultList)
                }
            }
        }
    }

    void destroy() { }
}
