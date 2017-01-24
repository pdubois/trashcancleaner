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

## Protecting some specific nodes against deletion

It is possible to protect some specific nodes against deletion by the trashcancleaner specifying a value for `trashcan.cleaner.nodestoskip` in  `alfresco-global.properties`. The value is a comma separated list of `nodeRef` to protect.

Example:

```
trashcan.cleaner.nodestoskip=archive://SpacesStore/86936ddc-176c-4233-b5d0-647889e4bc15,archive://SpacesStore/e177ebcf-02f9-43d7-b9d7-a3118f1818e0
```

## Limiting duration of execution
Execution time of the cleaner can be configured specifying `trashcan.cleaner.cleanermaxrunningtime`in `alfresco-global.properties`. Time is specified in miliseconds. Next time cron expression will trigger, execution will resume.

Example:

```### run 4 hours max (1000*60*60*4)
trashcan.cleaner.cleanermaxrunningtime=14400000
```

## Control webscripts are available to disable, enable trashcan clean at will

Example:

```
curl -v "http://localhost:8080/alfresco/service/api/login?u=admin&pw=<your pw>"
* About to connect() to localhost port 8080 (#0)
*   Trying 127.0.0.1... connected
> GET /alfresco/service/api/login?u=admin&pw=admin HTTP/1.1
> User-Agent: curl/7.22.0 (x86_64-pc-linux-gnu) libcurl/7.22.0 OpenSSL/1.0.1 zlib/1.2.3.4 libidn/1.23 librtmp/2.3
> Host: localhost:8080
> Accept: */*
> 
< HTTP/1.1 200 OK
< Server: Apache-Coyote/1.1
< Cache-Control: no-cache
< Expires: Thu, 01 Jan 1970 00:00:00 GMT
< Pragma: no-cache
< Content-Type: text/xml;charset=UTF-8
< Content-Length: 104
< Date: Tue, 24 Jan 2017 14:40:27 GMT
< 
<?xml version="1.0" encoding="UTF-8"?>
<ticket>TICKET_0f96c193bd3f088c87cb5bbbcd663e55a373f0b9</ticket>
* Connection #0 to host localhost left intact
* Closing connection #0

curl -v "http://127.0.0.1:8080/alfresco/s/trashcan/disable?alf_ticket=TICKET_0f96c193bd3f088c87cb5bbbcd663e55a373f0b9"
* About to connect() to 127.0.0.1 port 8080 (#0)
*   Trying 127.0.0.1... connected
> GET /alfresco/s/trashcan/disable?alf_ticket=TICKET_0f96c193bd3f088c87cb5bbbcd663e55a373f0b9 HTTP/1.1
> User-Agent: curl/7.22.0 (x86_64-pc-linux-gnu) libcurl/7.22.0 OpenSSL/1.0.1 zlib/1.2.3.4 libidn/1.23 librtmp/2.3
> Host: 127.0.0.1:8080
> Accept: */*
> 
< HTTP/1.1 200 OK
< Server: Apache-Coyote/1.1
< Content-Length: 23
< Date: Tue, 24 Jan 2017 14:43:33 GMT
< 
* Connection #0 to host 127.0.0.1 left intact
* Closing connection #0
{"OLDSTATUS":"RUNNING"}

curl -v "http://127.0.0.1:8080/alfresco/s/trashcan/enable?alf_ticket=TICKET_0f96c193bd3f088c87cb5bbbcd663e55a373f0b9"
* About to connect() to 127.0.0.1 port 8080 (#0)
*   Trying 127.0.0.1... connected
> GET /alfresco/s/trashcan/enable?alf_ticket=TICKET_0f96c193bd3f088c87cb5bbbcd663e55a373f0b9 HTTP/1.1
> User-Agent: curl/7.22.0 (x86_64-pc-linux-gnu) libcurl/7.22.0 OpenSSL/1.0.1 zlib/1.2.3.4 libidn/1.23 librtmp/2.3
> Host: 127.0.0.1:8080
> Accept: */*
> 
< HTTP/1.1 200 OK
< Server: Apache-Coyote/1.1
< Content-Length: 24
< Date: Tue, 24 Jan 2017 14:45:13 GMT
< 
* Connection #0 to host 127.0.0.1 left intact
* Closing connection #0
{"OLDSTATUS":"DISABLED"}
```

Note: disable might take a bit of time depending how fast query retrieving elements to get rid of performs.

### Example of properties that must be defined in alfresco-global.properties

```
trashcan.cleaner.cleanermaxrunningtime=14400000
trashcan.cleaner.cron=0/30 * * * * ?
trashcan.cleaner.protected.day=1
trashcan.cleaner.pagelen=100
```

## Gotcha:
When installing the module with the Module Management Tool (MMT) spcify the "-force" option or remove manually "slf4j-api-1.7.5.jar" from the module.

 

 