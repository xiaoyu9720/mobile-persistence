 /*******************************************************************************
  Copyright � 2015, Oracle and/or its affiliates. All rights reserved.
   
  $revision_history$
  08-jan-2015   Steven Davelaar
  1.0           initial creation
 ******************************************************************************/
 package oracle.ateam.sample.mobile.v2.persistence.service;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import oracle.adf.model.datacontrols.device.DeviceManagerFactory;

import oracle.adfmf.framework.api.AdfmfJavaUtilities;

import oracle.ateam.sample.mobile.v2.persistence.manager.DBPersistenceManager;
import oracle.ateam.sample.mobile.v2.persistence.metadata.AttributeMappingOneToMany;
import oracle.ateam.sample.mobile.v2.persistence.metadata.ClassMappingDescriptor;
import oracle.ateam.sample.mobile.v2.persistence.model.Entity;
import oracle.ateam.sample.mobile.v2.persistence.util.EntityUtils;
import oracle.ateam.sample.mobile.util.ADFMobileLogger;


/**
 * Class that implements lazy loading (aka as Indirection) of child collections in a domain object.
 * For example, the Department object can have a getEmployees method of type IndirectList, then
 * on the first call of the getEmployees method, this class will execute a SQL statement to retrieve
 * the employees from the database and populate the list.
 * If the child collection is not returned with the parent payload, the find-all-in-parent method as
 * defined in persistenceMapping.xml is executed.
 */
public class IndirectList<E extends Entity> 
  implements java.util.List
{

  private static ADFMobileLogger sLog = ADFMobileLogger.createLogger(IndirectList.class);
  Entity entity;
  List delegate;
  AttributeMappingOneToMany mapping;

  public IndirectList(Entity entity, AttributeMappingOneToMany mapping)
  {
      this.mapping = mapping;
      this.entity = entity;
  }


  public int size()
  {
    return getDelegate().size();
  }

  public boolean isEmpty()
  {
    return getDelegate().isEmpty();
  }

  public boolean contains(Object o)
  {
    return getDelegate().contains(o);
  }

  public Iterator iterator()
  {
    return getDelegate().iterator();
  }

  public Object[] toArray()
  {
    return getDelegate().toArray();
  }

  public Object[] toArray(Object[] a)
  {
    return getDelegate().toArray(a);
  }

  public boolean add(Object o)
  {
    return getDelegate().add(o);
  }

  public void add(int index, Object element)
  {
    getDelegate().add(index, element);
  }

  public boolean remove(Object o)
  {
    return getDelegate().remove(o);
  }

  public Object remove(int index)
  {
    return getDelegate().remove(index);
  }

  public boolean containsAll(Collection c)
  {
    return getDelegate().containsAll(c);
  }

  public boolean addAll(Collection c)
  {
    return getDelegate().addAll(c);
  }

  public boolean addAll(int index, Collection c)
  {
    return getDelegate().addAll(index, c);
  }

  public boolean removeAll(Collection c)
  {
    return getDelegate().removeAll(c);
  }

  public boolean retainAll(Collection c)
  {
    return getDelegate().retainAll(c);
  }

  /**
   * When this method is called, the next time the list is accessed, the database will be queried again
   * to refresh the list.
   */
  public void clear()
  {
    delegate = null;
  }

  public Object get(int index)
  {
    return getDelegate().get(index);
  }

  public Object set(int index, Object element)
  {
    return getDelegate().set(index, element);
  }

  public int indexOf(Object o)
  {
    return getDelegate().indexOf(o);
  }

  public int lastIndexOf(Object o)
  {
    return getDelegate().lastIndexOf(o);
  }

  public ListIterator listIterator()
  {
    return getDelegate().listIterator();
  }

  public ListIterator listIterator(int index)
  {
    return getDelegate().listIterator(index);
  }

  public java.util.List subList(int fromIndex, int toIndex)
  {
    return getDelegate().subList(fromIndex, toIndex);
  }


  protected List getDelegate()
  {
    if (delegate == null)
    {
      if (entity.getIsNewEntity())
      {
        delegate = new ArrayList();
      }
      else
      {
        delegate = buildDelegate();        
      }
    }
    return delegate;
  }

  protected List buildDelegate()
  {
    final ClassMappingDescriptor referenceDescriptor = mapping.getReferenceClassMappingDescriptor();
    String status = DeviceManagerFactory.getDeviceManager().getNetworkStatus();
    boolean offline = "NotReachable".equals(status) || "unknown".equals(status);
    if (mapping.getAccessorMethod() != null && !offline)
    {
      sLog.fine("Getter method for attribute " + this.mapping.getAttributeName() +
                " called for the first time, calling find-all-in-parent web service method");
      final EntityCRUDService service = EntityUtils.getEntityCRUDService(referenceDescriptor);  
      boolean inBackground = service.isDoRemoteReadInBackground();
      if (inBackground)
      {
        Runnable runnable = new Runnable()
        {
          public void run()
          {
            DBPersistenceManager pm = EntityUtils.getLocalPersistenceManager(referenceDescriptor);
            List oldList = pm.findAllInParent(referenceDescriptor.getClazz(), entity, mapping);   
            // we don't want the child service to start another backgrounf thread, so we temporarily switch
            // off background read
            service.setDoRemoteReadInBackground(false);        
            service.doRemoteFindAllInParent(entity,mapping.getAttributeName());        
            service.setDoRemoteReadInBackground(true);        
            List newList = service.getEntityList(); 
            delegate = newList;
            entity.refreshChildEntityList(oldList, newList, referenceDescriptor.getClazz(), mapping.getAttributeName());
            AdfmfJavaUtilities.flushDataChangeEvent();
          }
        };
        Thread thread = new Thread(runnable);
        thread.start();        
      }
      else
      {
//        service.setDoRemoteReadInBackground(false);        
        service.doRemoteFindAllInParent(entity,mapping.getAttributeName());        
//        service.setDoRemoteReadInBackground(inBackground);
//        List result = service.getEntityList(); 
//        return result;        
      }
    }
    sLog.fine("Getter method for attribute " + this.mapping.getAttributeName() +
                " called for the first time, querying database to retrieve the content");
    DBPersistenceManager pm = EntityUtils.getLocalPersistenceManager(referenceDescriptor);
    List result = pm.findAllInParent(referenceDescriptor.getClazz(), entity, mapping);      
    return result;
  }
}
