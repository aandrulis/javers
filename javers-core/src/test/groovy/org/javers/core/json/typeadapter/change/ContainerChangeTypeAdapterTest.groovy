package org.javers.core.json.typeadapter.change

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.javers.core.diff.Change
import org.javers.core.diff.changetype.container.*
import org.javers.core.json.JsonConverter
import org.javers.core.model.SnapshotEntity
import org.joda.time.LocalDate
import org.joda.time.LocalDateTime
import spock.lang.Specification
import spock.lang.Unroll

import static org.javers.core.JaversTestBuilder.javersTestAssembly
import static org.javers.core.metamodel.object.InstanceIdDTO.instanceId

/**
 * @author bartosz walacik
 */
class ContainerChangeTypeAdapterTest extends Specification{

    @Unroll
    def "should deserialize #changeType_.simpleName with references"()  {
        given:
            def javers = javersTestAssembly()
            JsonConverter jsonConverter = javers.jsonConverter

            def json = new JsonBuilder()
            json  {
                changeType changeType_.simpleName
                globalId {
                    entity "org.javers.core.model.SnapshotEntity"
                    cdoId 1
                }
                commitMetadata {
                    author "author"
                    commitDate "2001-12-01T22:23:03"
                    id "1.0"
                }
                property propertyName
                elementChanges ([
                        {
                            elementChangeType "ElementValueChange"
                            index 1
                            leftValue {
                                entity "org.javers.core.model.SnapshotEntity"
                                cdoId 2
                            }
                            rightValue {
                                entity "org.javers.core.model.SnapshotEntity"
                                cdoId 3
                            }
                        },
                        {
                            elementChangeType "ValueAdded"
                            index 2
                            value{
                                entity "org.javers.core.model.SnapshotEntity"
                                cdoId 2
                            }
                        },
                        {
                            elementChangeType "ValueRemoved"
                            index 3
                            value{
                                 entity "org.javers.core.model.SnapshotEntity"
                                 cdoId 3
                            }
                        }

                ])
            }

        when:
            //println json.toPrettyString()
            ContainerChange change  = jsonConverter.fromJson(json.toString(), Change)

        then:
            change.class == changeType_
            change.property.name == propertyName
            change.getAffectedCdoId() == instanceId(1, SnapshotEntity)

            with(change.commitMetadata.get()) {
                author == "author"
                commitDate == new LocalDateTime("2001-12-01T22:23:03")
                id == "1.0"

            }
            with((ElementValueChange)change.changes[0]) {
                index == 1
                leftValue  == instanceId(2, SnapshotEntity)
                rightValue == instanceId(3, SnapshotEntity)
            }
            with((ValueAdded)change.changes[1]) {
                index == 2
                value  == instanceId(2, SnapshotEntity)
            }
            with((ValueRemoved)change.changes[2]) {
                index == 3
                value  == instanceId(3, SnapshotEntity)
            }

        where:
            changeType_ <<   [ListChange, ArrayChange, SetChange]
            propertyName << ["listOfEntities","arrayOfEntities","setOfEntities"]
    }

    @Unroll
    def "should serialize #changeType.simpleName with references"()  {
        given:
            def javers = javersTestAssembly()
            JsonConverter jsonConverter = javers.jsonConverter
            def affectedId = javers.instanceId(new SnapshotEntity(id:1))
            def ref2  = javers.idBuilder().instanceId(2,SnapshotEntity)
            def ref3 =  javers.idBuilder().instanceId(3,SnapshotEntity)
            def elementChanges = [new ElementValueChange(1, ref2, ref3),
                                  new ValueAdded  (2, ref2),
                                  new ValueRemoved(3, ref3)]

            def property =  affectedId.cdoClass.getProperty(propertyName)
            ContainerChange change = changeType.newInstance(affectedId, property, elementChanges)

        when:
            String jsonText = jsonConverter.toJson(change)

        then:
            def json = new JsonSlurper().parseText(jsonText)
            json.property == propertyName
            json.changeType == changeType.simpleName
            json.globalId
            json.elementChanges.size() == 3
            with(json.elementChanges[0]){
                elementChangeType == "ElementValueChange"
                index == 1
                leftValue.entity == "org.javers.core.model.SnapshotEntity"
                leftValue.cdoId  == 2
                rightValue.entity == "org.javers.core.model.SnapshotEntity"
                rightValue.cdoId  == 3
            }
            with(json.elementChanges[1]){
                elementChangeType == "ValueAdded"
                index == 2
                value.entity == "org.javers.core.model.SnapshotEntity"
                value.cdoId  == 2
            }
            with(json.elementChanges[2]){
                elementChangeType == "ValueRemoved"
                index == 3
                value.entity == "org.javers.core.model.SnapshotEntity"
                value.cdoId  == 3
            }

        where:
            changeType <<   [ListChange, ArrayChange, SetChange]
            propertyName << ["listOfEntities","arrayOfEntities","setOfEntities"]
    }

    @Unroll
    def "should serialize #changeType.simpleName with Values using custom TypeAdapter"()  {
        given:
            def javers = javersTestAssembly()
            JsonConverter jsonConverter = javers.jsonConverter
            def affectedId = javers.instanceId(new SnapshotEntity(id:1))

            def elementChanges = [new ElementValueChange(1, new LocalDate(2001,1,1), new LocalDate(2001,1,2)),
                                  new ValueAdded  (2,new LocalDate(2001,1,3)),
                                  new ValueRemoved(3,new LocalDate(2001,1,4))]

            def property =  affectedId.cdoClass.getProperty(propertyName)
            ContainerChange change = changeType.newInstance(affectedId, property, elementChanges)

        when:
            String jsonText = jsonConverter.toJson(change)

        then:
            def json = new JsonSlurper().parseText(jsonText)
            json.property == propertyName
            json.changeType == changeType.simpleName
            json.globalId
            json.elementChanges.size() == 3
            with(json.elementChanges[0]){
                elementChangeType == "ElementValueChange"
                index == 1
                leftValue  == "2001-01-01"
                rightValue == "2001-01-02"
            }
            with(json.elementChanges[1]){
                elementChangeType == "ValueAdded"
                index == 2
                value == "2001-01-03"
            }
            with(json.elementChanges[2]){
                elementChangeType == "ValueRemoved"
                index == 3
                value == "2001-01-04"
            }

        where:
            changeType <<   [ListChange, ArrayChange, SetChange]
            propertyName << ["listOfDates","arrayOfDates","setOfDates"]
    }

    @Unroll
    def "should deserialize #changeType_.simpleName with #javersType"()  {
        given:
        def javers = javersTestAssembly()
        JsonConverter jsonConverter = javers.jsonConverter

        def json = new JsonBuilder()
        json  {
            changeType changeType_.simpleName
            globalId {
                entity "org.javers.core.model.SnapshotEntity"
                cdoId 1
            }
            property propertyName
            elementChanges ([
                    {
                        elementChangeType "ElementValueChange"
                        index 1
                        leftValue val1given
                        rightValue val2given
                    },
                    {
                        elementChangeType "ValueAdded"
                        index 2
                        value val1given
                    },
                    {
                        elementChangeType "ValueRemoved"
                        index 3
                        value val2given
                    }
            ])
        }

        when:
        //println json.toPrettyString()
        ContainerChange change  = jsonConverter.fromJson(json.toString(), Change)

        then:
        change.class == changeType_
        change.property.name == propertyName
        change.affectedCdoId == instanceId(1, SnapshotEntity)
        with(change.changes[0]) {
            it.class == ElementValueChange
            index == 1
            leftValue  == val1expected
            rightValue == val2expected
        }
        with(change.changes[1]) {
            it.class == ValueAdded
            index == 2
            value  == val1expected
        }
        with(change.changes[2]) {
            it.class == ValueRemoved
            index == 3
            value  == val2expected
        }

        where:
        val1given    << [10]*3 + ["2001-01-10"]*3
        val2given    << [11]*3 + ["2001-01-11"]*3
        val1expected << [10]*3 + [new LocalDate(2001,1,10)]*3
        val2expected << [11]*3 + [new LocalDate(2001,1,11)]*3
        javersType   << ["Primitives"]*3 + ["Values"]*3
        changeType_  << [ListChange, ArrayChange, SetChange] * 2
        propertyName << ["listOfIntegers","arrayOfIntegers","setOfIntegers","listOfDates","arrayOfDates","setOfDates"]
    }

    @Unroll
    def "should serialize #changeType.simpleName with Primitives" () {
        given:
            def javers = javersTestAssembly()
            JsonConverter jsonConverter = javers.jsonConverter
            def affectedId = javers.instanceId(new SnapshotEntity(id:1))

            def elementChanges = [new ElementValueChange(1, 11, 12),
                                  new ValueAdded  (2,20),
                                  new ValueRemoved(3,30)]

            def property =  affectedId.cdoClass.getProperty(propertyName)
            ContainerChange change = changeType.newInstance(affectedId, property, elementChanges)

        when:
            String jsonText = jsonConverter.toJson(change)
            //println(jsonText)

        then:
            def json = new JsonSlurper().parseText(jsonText)
            json.property == propertyName
            json.changeType == changeType.simpleName
            json.globalId
            json.elementChanges.size() == 3
            with(json.elementChanges[0]){
                elementChangeType == "ElementValueChange"
                index == 1
                leftValue  == 11
                rightValue == 12
            }
            with(json.elementChanges[1]){
                elementChangeType == "ValueAdded"
                index == 2
                value == 20
            }
            with(json.elementChanges[2]){
                elementChangeType == "ValueRemoved"
                index == 3
                value == 30
            }

        where:
            changeType <<   [ListChange, ArrayChange, SetChange]
            propertyName << ["listOfIntegers","arrayOfIntegers","setOfIntegers"]
    }
}
