--
-- Streets_one_dir.sql
-- -------------------
--
-- Input:
--     navteq."Streets_new"  
--     navteq."Zlevels"
--
-- The schema itrouting2 will be created
--
-- functions that will be created on schema itrouting2:
--
--    get_cost(double precision, double precision, integerm integer) 
--
-- Tables that will be created on schema itrouting2:
--
--    Streets_one_dir
--    Streets_one_dir2
--


-- drop
drop schema itrouting2 cascade;
drop function if exists itrouting2.get_cost(double precision, double precision, integer, integer);
drop table if exists itrouting2."Streets_one_dir";
drop table if exists itrouting2."Streets_one_dir2";
drop table if exists itrouting2.sod_nodes;

-- create
create schema itrouting2;

create or replace function itrouting2.get_cost(speed_cat double precision, link_length double precision, lane_cat integer, func_class integer)
returns double precision
as
$$
declare
    tvl double precision;
begin
    tvl := link_length;
    
    select 
        ((link_length) / (
            case 
                when speed_cat = 1.0 then 130.0
                when speed_cat = 2.0 then 120.0
                when speed_cat = 3.0 then 95.0
                when speed_cat = 4.0 then 80.0
                when speed_cat = 5.0 then 60.0
                when speed_cat = 6.0 then 40.0
                when speed_cat = 7.0 then 20.0
                else 10
            end
        ))*3600.0
    into tvl;
    
    return tvl + tvl*(1 - lane_cat/3.0)*(func_class/5.0);
end;
$$
language 'plpgsql';

create table itrouting2."Streets_one_dir" (
    id integer,
    old_id integer,
    source integer,
    target integer,
    cost double precision,
    max_time_cat double precision,
    dir_link boolean,
    x1 double precision,
    y1 double precision,
    x2 double precision,
    y2 double precision,
    both_dir boolean
);
select AddGeometryColumn('itrouting2','Streets_one_dir','the_geom',4326,'LINESTRING',2);

create table itrouting2."Streets_one_dir2" (
    --id serial,
    id_ori integer,
    source integer,
    target integer,
    source_ori integer,
    target_ori integer,
    cost_source double precision,
    cost_target double precision,
    max_time_cat_source double precision,
    max_time_cat_target double precision,
    dir_link_source boolean,
    dir_link_target boolean,
    linestring_source text,
    linestring_target text,
    length double precision,
    x1 double precision,
    y1 double precision,
    x2 double precision,
    y2 double precision
);
select AddGeometryColumn('itrouting2','Streets_one_dir2','the_geom',4326,'LINESTRING',2);

insert into itrouting2."Streets_one_dir"
select 
    "id" - 1000000000 as link_id,
    "id"  as old_link_id,
    "source",
    "target",
    itrouting2.get_cost("speed_cat"::double precision,ST_Length(ST_Transform(the_geom,26986))/1000.0,"lane_cat"::integer,"func_class"::integer),
    0.0,
    true,    
    ST_X(ST_StartPoint(the_geom)),
    ST_Y(ST_StartPoint(the_geom)),
    ST_X(ST_EndPoint(the_geom)),
    ST_Y(ST_EndPoint(the_geom)),
    not "dir_t",
    the_geom
from traffic."graph_streets_dc"
    union
select 
    "id",
    "id",
    "target",
    "source",
    itrouting2.get_cost("speed_cat"::double precision,ST_Length(ST_Transform(the_geom,26986))/1000.0,"lane_cat"::integer,"func_class"::integer),
    0.0,
    false,
    ST_X(ST_EndPoint(the_geom)),
    ST_Y(ST_EndPoint(the_geom)),
    ST_X(ST_StartPoint(the_geom)),
    ST_Y(ST_StartPoint(the_geom)),
    not "dir_t",
    ST_Reverse(the_geom)
from traffic."graph_streets_dc"
where not "dir_t"
order by old_link_id;

--
-- mejora de costos
--

--
-- tiempo maximo
--

update itrouting2."Streets_one_dir" as sod
set max_time_cat = 
    (ST_Length(ST_Transform(sod.the_geom,26986)) / (case
        when s."speed_cat" = '1' then 500.0*0.277777778
        when s."speed_cat" = '2' then 130.0*0.277777778
        when s."speed_cat" = '3' then 100.0*0.277777778
        when s."speed_cat" = '4' then 90.0*0.277777778
        when s."speed_cat" = '5' then 70.0*0.277777778
        when s."speed_cat" = '6' then 50.0*0.277777778
        when s."speed_cat" = '7' then 30.0*0.277777778
        else 11*0.277777778
    end))
from traffic."graph_streets_dc" as s
where old_id = s."id";

--
--
--

insert into itrouting2."Streets_one_dir2"(
    id_ori,
    source,
    target,
    source_ori,
    target_ori,
    cost_source,
    cost_target,
    max_time_cat_source,
    max_time_cat_target,
    dir_link_source,
    dir_link_target,
    linestring_source,
    linestring_target,
    x1,
    y1,
    x2,
    y2
)
select 
    a.target, 
    a.id, 
    b.id, 
    a.old_id, 
    b.old_id, 
    a.cost, 
    b.cost,
    a.max_time_cat,
    b.max_time_cat,
    a.dir_link,
    b.dir_link,
    /*ST_AsText(a.the_geom),
    ST_AsText(b.the_geom),*/
    substring(ST_AsText(a.the_geom),12,length(ST_AsText(a.the_geom))-12),
    substring(ST_AsText(b.the_geom),12,length(ST_AsText(b.the_geom))-12),
    /*case
        when ST_NumPoints(a.the_geom) > 2 then
            ST_X(ST_PointN(a.the_geom,ceil(ST_NumPoints(a.the_geom)/2)::integer))
        else 
            (a.x1 + a.x2) / 2 
    end as x1, 
    case
        when ST_NumPoints(a.the_geom) > 2 then
            ST_Y(ST_PointN(a.the_geom,ceil(ST_NumPoints(a.the_geom)/2)::integer))
        else 
            (a.y1 + a.y2) / 2 
    end as y1,
    case
        when ST_NumPoints(b.the_geom) > 2 then
	    ST_X(ST_PointN(b.the_geom,ceil(ST_NumPoints(b.the_geom)/2)::integer))
        else 
            (b.x1 + b.x2) / 2 
    end as x2,
    case
        when ST_NumPoints(b.the_geom) > 2 then
            ST_Y(ST_PointN(b.the_geom,ceil(ST_NumPoints(b.the_geom)/2)::integer))
        else 
            (b.y1 + b.y2) / 2 
    end as y2*/
    /*(a.x1 + a.x2) / 2 as x1,
    (a.y1 + a.y2) / 2 as y1,
    (b.x1 + b.x2) / 2 as x2,
    (b.y1 + b.y2) / 2 as y2*/
    ST_X(ST_line_interpolate_point(a.the_geom,0.5)) as x1,
    ST_Y(ST_line_interpolate_point(a.the_geom,0.5)) as y1,
    ST_X(ST_line_interpolate_point(b.the_geom,0.5)) as x2,
    ST_Y(ST_line_interpolate_point(b.the_geom,0.5)) as y2
from itrouting2."Streets_one_dir" as a
join itrouting2."Streets_one_dir" as b
on a.target = b.source;

update itrouting2."Streets_one_dir2"
set the_geom = ST_GeomFromText('LINESTRING('||x1||' '||y1||','||x2||' '||y2||')',4326);

update itrouting2."Streets_one_dir2"
set length = ST_Length(ST_Transform(the_geom,26986))/1000.0;

create index streets_on_dir2_the_geomx on itrouting2."Streets_one_dir2" using gist(the_geom);
create index streets_on_dir_idx on itrouting2."Streets_one_dir"(id);
create index streets_on_dir_the_geomx on itrouting2."Streets_one_dir" using gist(the_geom);

delete from itrouting2."Streets_one_dir2"
where source_ori = target_ori;

alter table itrouting2."Streets_one_dir2" add column id serial;

create index streets_on_dir2_idx on itrouting2."Streets_one_dir2"(id);
--
-- OJO
--

create table itrouting2.sod_nodes (
    id serial,
    node integer,
    cost double precision,
    length double precision,
    max_time_cat double precision,
    dir_link boolean,
    node_ori integer,
    linestring text,
    x double precision,
    y double precision
);

insert into itrouting2.sod_nodes (
    node,
    cost,
    max_time_cat,
    dir_link,
    node_ori,
    linestring,
    x,
    y
)
select 
    source as node, 
    cost_source as cost, 
    max_time_cat_source as max_time_cat,
    dir_link_source, 
    source_ori as node_ori, 
    linestring_source as linestring, 
    x1 as x, 
    y1 as y 
from itrouting2."Streets_one_dir2" 
    union 
select 
    target as node, 
    cost_target, 
    max_time_cat_target, 
    dir_link_target,
    target_ori, 
    linestring_target, 
    x2, 
    y2 
from itrouting2."Streets_one_dir2";

alter table itrouting2.sod_nodes add primary key(id);
alter table itrouting2."Streets_one_dir2" add primary key(id);

update itrouting2.sod_nodes as x
set length = ST_length(ST_transform(sod.the_geom,26986))
from itrouting2."Streets_one_dir" as sod
where sod.id = x.node;

update itrouting2.sod_nodes as x
set 
    x = ST_X(ST_line_interpolate_point(ST_transform(sod.the_geom,26986),0.5)),
    y = ST_Y(ST_line_interpolate_point(ST_transform(sod.the_geom,26986),0.5))
from itrouting2."Streets_one_dir" as sod
where sod.id = x.node;

drop function if exists itrouting2.get_nearest_edge_by_coor(double precision, double precision,double precision);

create or replace function itrouting2.get_nearest_edge_by_coor(x_1 double precision, y_1 double precision, azimuth double precision default -1, out distance double precision, out link_id integer,out source_id integer, out target_id integer)
returns record as
$$
declare
    eps integer;
    edge_id integer;
    point_row record;
    point geometry;
begin
    select ST_GeomFromText('POINT('||x_1||' '||y_1||')',4326)
    into point;
    eps := 30;
    select 
        id, 
        source, 
        target,
        --ST_DIstance(the_geom,point) as dist,
        both_dir,
        least(
                abs((ST_azimuth(ST_StartPoint(the_geom),ST_EndPoint(the_geom))) * 180.0 / pi() - azimuth),
                360 - abs((ST_azimuth(ST_StartPoint(the_geom),ST_EndPoint(the_geom))) * 180.0 / pi() - azimuth)
        ) as dif,
        the_geom
    into point_row
    from itrouting2."Streets_one_dir"
    where ST_Dwithin(the_geom,point,0.001)
    order by ST_DIstance(the_geom,point)
    limit 1;

    if point_row.dif > eps and azimuth <> -1 and point_row.both_dir then
	select id, source, target
	from (
	   select 
		id, 
		source, 
		target,
		--ST_DIstance(the_geom,point) as dist,
		least(
                    abs((ST_azimuth(ST_StartPoint(the_geom),ST_EndPoint(the_geom))) * 180.0 / pi() - azimuth),
                    360 - abs((ST_azimuth(ST_StartPoint(the_geom),ST_EndPoint(the_geom))) * 180.0 / pi() - azimuth)
                ) as dif,
		the_geom
	    from itrouting2."Streets_one_dir"
	    where ST_Dwithin(the_geom,point,0.001)
	    order by ST_DIstance(the_geom,point)
	    limit 2
	) as foo
	into point_row
	order by
	least(
             abs((ST_azimuth(ST_StartPoint(the_geom),ST_EndPoint(the_geom))) * 180.0 / pi() - azimuth),
             360 - abs((ST_azimuth(ST_StartPoint(the_geom),ST_EndPoint(the_geom))) * 180.0 / pi() - azimuth)
        )
	limit 1;
    end if;
    distance := 0.0; --point_row.dist;
    link_id := point_row.id;
    source_id := point_row.source;
    target_id := point_row.target;

end;
$$
language 'plpgsql';

--
-- preference
--


