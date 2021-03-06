package org.ring.dml.transaction.save;

import org.ring.dml.transaction.DmlTransaction;
import org.ring.dml.transaction.DmlType;
import org.ring.dml.transaction.joint.Joint;
import org.ring.entity.EntityManager;
import org.ring.entity.Mapper;
import org.ring.exception.InvalidDataException;
import org.ring.meta.annotation.entity.Cascade;
import org.ring.meta.annotation.relationship.ManyToMany;
import org.ring.meta.annotation.relationship.OneToMany;
import org.ring.orm.OrmFactory;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by quanle on 6/2/2017.
 */
public class SaveTransaction extends DmlTransaction
{
    private HashMap<Class<?>, AbstractPersistence> dmlMap = new HashMap<>();
    private HashMap<String, InsertJoint> jointMap = new HashMap<>();

    public SaveTransaction(DmlType dmlType)
    {
        this.dmlType = dmlType;
    }

    @Override
    public Object execute(Connection connection, Object data)
    {
        try
        {
            parse(data, data.getClass());

            dmlMap.forEach((type, batch) ->
            {
                batch.execute(connection);
            });

            dmlMap.forEach((type, batch) ->
            {
                batch.updateForeignKey(connection);
            });

            dmlMap.forEach((type, batch) ->
            {
                batch.updateReferencedColumn(connection);
            });

            jointMap.forEach((type, batch) ->
            {
                batch.execute(connection);
            });

            Mapper mapper = EntityManager.getMapper(data.getClass());
            Field idField = mapper.getIdField();
            return idField.get(data);
        }
        catch (SQLException | IllegalAccessException | InvalidDataException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    private void parse(Object data, Class<?> type) throws SQLException, IllegalAccessException, InvalidDataException
    {
        if (data == null)
        {
            return;
        }

        type = EntityManager.original(type);
        Mapper mapper = EntityManager.getMapper(type);
        AbstractPersistence dml = dmlMap.get(type);
        if (dml == null)
        {
            dml = OrmFactory.newPersistence(mapper.isIdAutoGenerated());
            dml.setMapper(mapper);
            dmlMap.put(type, dml);
        }

        if (dml.add(data))
        {
            for (Field field : mapper.getForeignKeys())
            {
                if (DmlType.checkCascade(field.getAnnotation(Cascade.class), dmlType))
                {
                    parse(field.get(data), field.getType());
                }
            }

            parseReferencedColumns(data, mapper.getMembers(OneToMany.class),
                    field -> field.getAnnotation(OneToMany.class).entity(),
                    (item, field) ->
                    {
                    }
            );

            parseReferencedColumns(data, mapper.getMembers(ManyToMany.class),
                    field -> field.getAnnotation(ManyToMany.class).entity(),
                    (item, field) ->
                    {
                        Mapper memberMapper = EntityManager.getMapper(item.getClass());
                        Object memberId = memberMapper.getId(item);
                        Object id = mapper.getId(data);
                        if (id == null || memberId == null)
                        {
                            String table = memberMapper.getTable();
                            InsertJoint joint = jointMap.get(table);
                            if (joint == null)
                            {
                                joint = new InsertJoint(data.getClass(), field.getAnnotation(ManyToMany.class));
                                jointMap.put(table, joint);
                            }
                            joint.add(new Joint(data, item));
                        }
                    }
            );
        }
    }

    private void parseReferencedColumns(Object data, Field[] referencedColumns, GetAssociation getAssociation, AddJoinTable addJoinTable) throws IllegalAccessException, SQLException, InvalidDataException
    {
        for (Field field : referencedColumns)
        {
            Object obj = field.get(data);
            if (obj instanceof Collection && DmlType.checkCascade(field.getAnnotation(Cascade.class), dmlType))
            {
                Collection collection = (Collection) obj;
                Class<?> type = getAssociation.invoke(field);
                for (Object item : collection)
                {
                    addJoinTable.invoke(item, field);
                    parse(item, type);
                }
            }
        }
    }

    private interface AddJoinTable
    {
        void invoke(Object data, Field field) throws IllegalAccessException;
    }

    private interface GetAssociation
    {
        Class<?> invoke(Field field);
    }
}



/* @Override
    protected void parse(Object data, Class<?> type) throws SQLException, IllegalAccessException, InvalidDataException
    {
        if (data == null)
        {
            return;
        }

        Mapper mapper = EntityManager.getMapper(type);
        AbstractPersistence batch = dmlMap.get(type);
        if (batch == null)
        {
            batch = OrmFactory.newPersistence();
            batch.setMapper(mapper);
            dmlMap.put(type, batch);
        }

        if (batch.add(data))
        {
            for (Field field : mapper.getForeignKeys())
            {
                if (Division.checkCascade(field.getAnnotation(Cascade.class), dmlType))
                {
                    parse(field.get(data), field.getType());
                }
            }

            parseReferencedColumns(data, mapper.getMembers(OneToOne.class),
                    field -> field.getAnnotation(OneToMany.class).entity(),
                    (item, field) ->
                    {
                    }
            );

            parseReferencedColumns(data, mapper.getMembers(ManyToMany.class),
                    field -> field.getAnnotation(ManyToMany.class).entity(),
                    (item, field) ->
                    {
                        Mapper memberMapper = EntityManager.getMapper(item.getClass());
                        Object memberId = memberMapper.getId(item);
                        Object id = mapper.getId(data);
                        if (id == null || memberId == null)
                        {
                            String table = memberMapper.getTable();
                            InsertJoint jointBatch = jointMap.get(table);
                            if (jointBatch == null)
                            {
                                jointBatch = new InsertJoint(data.getClass(), field.getAnnotation(ManyToMany.class));
                                jointMap.put(table, jointBatch);
                            }
                            jointBatch.add(new Joint(data, item));
                        }
                    }
            );
        }
    }*/