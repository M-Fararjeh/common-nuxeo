# Meeting Management

Foobar is a Python library for dealing with word pluralization.

## Installation

use maven command to install the project

```bash
mvn clean install -DnuxeoBranch=master-SNAPSHOT
```

## How to use
You have to add these lines in the 'settings.xml' in conf folder of Maven

```maven
<server>
	<id>nuxeo-studio</id>
	<username>your-email@company.com</username>
	<password>your token generated from Nuxeo Studio</password>
</server>

```
## Note
be sure that you have only one 'nuxeo-studio' under the server