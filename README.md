# trashcancleaner
Alfresco trashcancleaner using canned queries for purging a large number of nodes from the bin.

## Building the module:

```
mkdir work
cd work
git clone https://github.com/pdubois/trashcancleaner.git
cd trashcancleaner/alfrescotrashcancleaner2
mvn install
chmod +x run.sh
```

## Outcome:
The module "alfrescotrashcancleaner2.amp" is generqted under "target" folder.

## Module options:

You need to adjust to your needs the following options in you `alfresco-global.properties` depending on how long you want to keep the documents in the bin and how often the `thrahcancleaner` will run.

`trashcan.cleaner.pagelen` indicates the maximum number of nodes purged during each iteration/transaction.

```
trashcan.cleaner.protected.day=7
trashcan.cleaner.cron=0 0 4 * * ?
trashcan.cleaner.pagelen=3
```
## Protecting types from deletion

It is possible to protect some types from  automatic deletion by the trashcancleaner by overriding trashcanCleaner bean definition under 
shared/classes/alfresco/extention in a *-context.xml file.


### Example:

```
	<bean id="trashcanCleaner" class="org.alfresco.trashcan.cleaner.TrashcanCleaner">
		<property name="nodeService">
			<ref bean="nodeService" />
		</property>
		<property name="transactionService">
			<ref bean="TransactionService" />
		</property>
		<property name="protectedDays">
			<value>${trashcan.cleaner.protected.day}</value>
		</property>
		<property name="dictionaryService">
			<ref bean="DictionaryService" />
		</property>
		<property name="jobLockService">
            <ref bean="jobLockService" />
        </property>
        <property name="searchService">
            <ref bean="searchService" />
        </property>
		<property name="pageLen" value="${trashcan.cleaner.pagelen}" />
		<!-- Set of type that must be protected from deletetion -->
		<!-- results in a setAddressSet(java.util.Set) call -->
		<property name="setToProtect">
			<set>
			    <!-- Example: QName of type to protect -->
				<!-- value>{http://www.alfresco.org/model/site/1.0}site</value -->
				<!-- THIS TYPE MUSE ALWAYS BE PROTECTED: {http://www.alfresco.org/model/system/1.0}archiveUser -->
				<value>{http://www.alfresco.org/model/system/1.0}archiveUser</value>
			</set>
		</property>
		<property name="nodesToSkip">
            <value>${trashcan.cleaner.nodestoskip}</value>
        </property>
        <property name="cleanerMaxRunningTime">
            <value>${trashcan.cleaner.cleanermaxrunningtime}</value>
        </property>
	</bean>
```

## Protecting some specific nodes against deletion

It is possible to protect some specific nodes against deletion by the trashcancleaner specifying a value for `trashcan.cleaner.nodestoskip` in  `alfresco-global.properties`. The value is a comma separated list of `nodeRef` to protect.

Example:

```
trashcan.cleaner.nodestoskip=archive://SpacesStore/86936ddc-176c-4233-b5d0-647889e4bc15,archive://SpacesStore/e177ebcf-02f9-43d7-b9d7-a3118f1818e0
```

## Limiting duration of execution
Execution time of the cleaner can be configured specifying `trashcan.cleaner.cleanermaxrunningtime`in `alfresco-global.properties`. Time is specified in miliseconds. Next time cron expression will trigger, execution will resume.

Example:

```
trashcan.cleaner.cleanermaxrunningtime=14400000
```

## Control webscripts are available to disable, enable trashcan clean at will

Example:

```
 curl -v http://localhost:8080/alfresco/service/api/login?u=admin&pw=<your pw>
 curl -v http://127.0.0.1:8080/alfresco/s/trashcan/disable?alf_ticket=TICKET_0f96c193bd3f088c87cb5bbbcd663e55a373f0b9
 curl -v http://127.0.0.1:8080/alfresco/s/trashcan/enable?alf_ticket=TICKET_0f96c193bd3f088c87cb5bbbcd663e55a373f0b9

```

Note: disable might take a bit of time depending how fast query retrieving elements to get rid of performs. You need to use ticket returned while executing first line in the example above.

### Example of properties that must be defined in alfresco-global.properties

```
trashcan.cleaner.cleanermaxrunningtime=14400000
trashcan.cleaner.cron=0/30 * * * * ?
trashcan.cleaner.protected.day=1
trashcan.cleaner.pagelen=100
```

## Gotcha:
When installing the module with the Module Management Tool (MMT) spcify the "-force" option or remove manually "slf4j-api-1.7.5.jar" from the module.

 

 