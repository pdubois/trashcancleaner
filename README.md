# trashcancleaner
Alfresco trashcancleaner using canned queries for purging a large number of nodes from the bin. The  SDK-2.1.0 branch is 
build using alfresco SDK2.1.0. meaning compatible with alfresco versions 5.0.1+ and 5.0.d+ see http://docs.alfresco.com/5.0/concepts/alfresco-sdk-compatibility.html.
This branch is based on the alfresco-amp-archetype, see http://docs.alfresco.com/5.0/tasks/alfresco-sdk-tutorials-all-in-one-archetype.html

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
		<property name="storeUrl">
			<value>archive://SpacesStore</value>
		</property>
		<property name="dictionaryService">
			<ref bean="DictionaryService" />
		</property>
		<property name="cannedQueryRegistry">
			<ref bean="fileFolderCannedQueryRegistry" />
		</property>
		<property name="pageLen" value="${trashcan.cleaner.pagelen}" />
		<!-- Set of type that must be protected from deletetion -->
		<!-- results in a setAddressSet(java.util.Set) call -->
		<property name="setToProtect">
			<set>
			    <!-- Example: QName of type to protect -->
			    <!-- Long version of the prefix QName must be used -->
				<value>{http://www.alfresco.org/model/site/1.0}site</value>
			</set>
		</property>
	</bean>
```



## Gotcha:
When installing the module with the Module Management Tool (MMT) spcify the "-force" option or remove manually "slf4j-api-1.7.5.jar" from the module.

 

 