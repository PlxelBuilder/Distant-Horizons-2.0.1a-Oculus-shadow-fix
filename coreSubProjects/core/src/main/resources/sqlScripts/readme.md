
### All Sql scripts should be run exactly once per database and old scripts shouldn't be changed. Any necessary schema changes should be done by creating new scripts that modify the existing database.

This system is roughly based on the DbUp library from .NET, for information about DbUp and it's general philosophy please refer to the following doc:
https://dbup.readthedocs.io/en/latest/philosophy-behind-dbup/

<br>

### Adding New Scripts:
New scripts must be added to the "scriptList.txt" file, otherwise they will not be run. <br>
(If anyone has a good way to automatically pull all resource files ending in `.sql` instead, please let us know.)
 
<br>

### File Naming:
- The first 3 numbers are major scripts.
- The 4th number is for minor/related scripts or if a bug fix needs to be applied between scripts.
- flavor of database the script is for (for now this is just sqlite)
- description of the script

<br>

### Mutli-query Scripts: 
When creating a script with multiple queries the queries must be separated with the SQL comment `--batch--` otherwise only the first query will be executed.

Example:
```roomsql
CREATE TABLE TableOne(
     DhSectionPos TEXT NOT NULL PRIMARY KEY 
    ,Data BLOB NULL
);

--batch--

CREATE TABLE TableTwo(
     DhSectionPos TEXT NOT NULL PRIMARY KEY 
    ,Data BLOB NULL
);
```



