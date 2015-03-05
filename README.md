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
## Gotcha:
When installing the module with the Module Management Tool (MMT) spcify the "-force" option or remove manually "slf4j-api-1.7.5.jar" from the module.

 

 