create table IF NOT EXISTS locations (id integer primary key AUTO_INCREMENT  ,name VARCHAR);
create table IF NOT EXISTS sensortypes (id integer primary key AUTO_INCREMENT  ,type VARCHAR, regression_error double, measure_error double );
create table IF NOT EXISTS data (id integer primary key AUTO_INCREMENT,sensortype_id integer foreign key REFERENCES sensortypes(id), location_id integer foreign key REFERENCES locations(id), value double, measure_time timestamp, measured boolean);

insert into locations  (id, name) values (1,'ut');
insert into sensortypes values (1, 'temp', 0.5, 0.1);
insert into sensortypes values (2,'hall', 2.5, 0.5);
insert into sensortypes values (3, 'ldr', 50, 10);
insert into sensortypes values (4, 'battery status', 0.1, 0.1);
insert into sensortypes values (5, 'cpu', 50, 10);
/*data predefined sensors
insert into sensortypes values(1,"");
insert into sensortypes values(2,"");
insert into sensortypes values(3,"");*/
