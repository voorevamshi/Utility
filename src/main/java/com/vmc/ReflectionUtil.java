package com.vmc.util;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

public class ReflectionUtil {
	private enum ClassArgType { ACTUAL_ARG, RAW }
	
	public static class ReflectionUtilException extends Exception {
		private static final long serialVersionUID = 1L;
		public ReflectionUtilException(String arg0) { super(arg0); }
		public ReflectionUtilException(Throwable arg0) { super(arg0); }
		public ReflectionUtilException(String arg0, Throwable arg1) { super(arg0, arg1); }
	}

	public static class ReflectionUtilClassNotSupportedException extends Exception {
		private static final long serialVersionUID = 1L;
		public ReflectionUtilClassNotSupportedException(String arg0) { super(arg0); }
		public ReflectionUtilClassNotSupportedException(Throwable arg0) { super(arg0); }
		public ReflectionUtilClassNotSupportedException(String arg0, Throwable arg1) { super(arg0, arg1); }
	}

	public static class ReflectionUtilDateException extends Exception {
		private static final long serialVersionUID = 1L;
		public ReflectionUtilDateException(String arg0) { super(arg0); }
		public ReflectionUtilDateException(Throwable arg0) { super(arg0); }
		public ReflectionUtilDateException(String arg0, Throwable arg1) { super(arg0, arg1); }
	}

	public static Object copyBean(Object src, Object dst) throws ReflectionUtilException {
		return copyBean(src, dst, true);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Object copyBean(Object src, Object dst, boolean recursive) throws ReflectionUtilException {
		if (src == null) return null;
		
		if (dst == null) {
			throw new ReflectionUtilException("Destination bean must be initialized (not null).");
		}
		
		Object result = dst;
		try {
			for (Method srcMethod : src.getClass().getMethods()) {
				if (!isBeanSourceMethod(srcMethod)) {
					// If the bean method doesn't match source method criteria skip it.
					continue;
				}
				try {
					Method dstMethod = null;
					String srcPrefix = (srcMethod.getName().startsWith("is"))?"is":"get";
					Class srcClass=srcMethod.getReturnType();

					Class dstClass = null;
					try {
						dstClass = dst.getClass().getMethod(srcMethod.getName(), new Class[]{}).getReturnType();
					} catch (NoSuchMethodException ignore) {
						// Boolean get methods for Axis clients may use "get" instead of "is".  If the source
						// prefix is "is", check for a "get" method in the destination.
						if (srcPrefix.equals("is")) {
							try {
								dstClass = dst.getClass().getMethod(srcMethod.getName().replaceFirst(srcPrefix, "get"), new Class[]{}).getReturnType();
							} catch (NoSuchMethodException ignoreAgain) { continue; }
						} else {
							continue;
						}
					}

					try {
						if (Collection.class.isAssignableFrom(dstClass)) {
							dstMethod = dst.getClass().getMethod(srcMethod.getName(),new Class[]{});
						} else {
							dstMethod = dst.getClass().getMethod(srcMethod.getName().replaceFirst(srcPrefix, "set"),new Class[]{dstClass});
						}
					} catch (NoSuchMethodException ignore) { continue; }

					// Arrays and collections
					if (srcClass.isArray() || Collection.class.isAssignableFrom(srcClass)) {
						Collection srcCol, dstCol;
						Object[] srcArray;
						
						// Make sure the destination is a collection or array
						if (!(dstClass.isArray() || Collection.class.isAssignableFrom(dstClass))) continue;

						// Get source and destination classes for the objects in the collection/array
						Class srcObjClass, dstObjClass;
						if (srcClass.isArray()) {
							srcObjClass = (Class)srcClass.getComponentType();
						} else {
							srcObjClass = getClassForArgType(ClassArgType.ACTUAL_ARG, srcMethod.getGenericReturnType());
						}
						if (dstClass.isArray()) {
							dstObjClass = (Class)dstClass.getComponentType();
						} else {
							dstObjClass = getClassForArgType(ClassArgType.ACTUAL_ARG, dst.getClass().getMethod(srcMethod.getName(), new Class[]{}).getGenericReturnType());
						}

						// Debug info
						//System.out.println("srcClass: " + srcClass.getName());
						//System.out.println("dstClass: " + dstClass.getName());
						//System.out.println("srcObjClass: " + srcObjClass.getName());
						//System.out.println("dstObjClass: " + dstObjClass.getName());

						// Initialize destination collections, if null
						if (!dstClass.isArray() && (dstMethod.invoke(dst, new Object[]{}) == null)) {
							// get the setter method and create a new instance of the list
							String setterMethodName = dstMethod.getName().replaceFirst(srcPrefix, "set");
							try {
								Method dstSetterMethod = dst.getClass().getMethod(setterMethodName, new Class[]{dstClass});
								if (List.class.isAssignableFrom(dstClass)) {
									dstSetterMethod.invoke(dst, new Object[]{new ArrayList()});
								} else {
									continue; // Destination collection type not supported
								}
							} catch (NoSuchMethodException ignored) {
								// if there is no setter and the collection object is null, can't copy
								continue;
							}
						}

						// If the destination is a collection, clear it.
						if (!dstClass.isArray()) ((Collection)dstMethod.invoke(dst, new Object[]{})).clear();

						// Copy source to destination
						if (srcObjClass.equals(dstObjClass)) {
							// src/dst objects in collection/array are identical
							if (srcClass.isArray() && dstClass.isArray()) {
								srcArray = (Object[])srcMethod.invoke(src, new Object[]{});
								if (srcArray != null) {
									dstMethod.invoke(dst, new Object[]{srcArray.clone()});
								}
							} else if (!srcClass.isArray() && !dstClass.isArray()) {
								srcCol = (Collection)srcMethod.invoke(src, new Object[]{});
								if (srcCol != null) {
									((Collection)dstMethod.invoke(dst, new Object[]{})).addAll(srcCol);
								}
							} else if (srcClass.isArray() && !dstClass.isArray()) {
								// TODO: Test this (not tested)
								srcArray = (Object[])srcMethod.invoke(src, new Object[]{});
								if (srcArray != null) {
									((Collection)dstMethod.invoke(dst, new Object[]{})).addAll(Arrays.asList(srcArray));
								}
							} else if (!srcClass.isArray() && dstClass.isArray()) {
								// TODO: Test this (not tested)
								srcCol = (Collection)srcMethod.invoke(src, new Object[]{});
								if (srcCol != null) {
									dstMethod.invoke(dst, new Object[]{srcCol.toArray((Object[]) Array.newInstance(srcObjClass, srcCol.size()))});
								}
							}
						} else {
							if (srcClass.isArray()) {
								srcArray = (Object[])srcMethod.invoke(src, new Object[]{});
								if (srcArray != null) {
									srcCol = Arrays.asList(srcArray);
								} else {
									srcCol = new ArrayList();
								}
							} else {
								srcCol = (Collection)srcMethod.invoke(src, new Object[]{});
							}
							if (dstClass.isArray()) {
								dstCol = new ArrayList();
							} else {
								dstCol = (Collection)dstMethod.invoke(dst, new Object[]{});
							}
							if (srcCol != null) {
								for (Object srcObj : srcCol) {
									Object dstObj = null;
									if (srcObj != null) {
										dstObj = dstObjClass.getConstructor(new Class[]{}).newInstance(new Object[]{});
										copyBean(srcObj, dstObj, true);
									}
									dstCol.add(dstObj);
								}
							}
							if (dstClass.isArray()) {
								dstMethod.invoke(dst, new Object[]{dstCol.toArray((Object[]) Array.newInstance(dstObjClass, dstCol.size()))});
							}
						}
						continue; // end of arrays and collections
					}

					// deal with the date conversion nightmare...
					if(dstClass.isAssignableFrom(XMLGregorianCalendar.class)) {
						dstMethod.invoke(dst,new Object[]{toXMLGregorianCalendar(srcMethod.invoke(src,new Object[]{}))} );
					} else if(dstClass.isAssignableFrom(Calendar.class)) {
						dstMethod.invoke(dst,new Object[]{toCalendar(srcMethod.invoke(src,new Object[]{}))} );
					} else if(dstClass.isAssignableFrom(Date.class)) {
						dstMethod.invoke(dst,new Object[]{toDate(srcMethod.invoke(src,new Object[]{}))} );
					} else if(dstClass.isAssignableFrom(srcClass)) {
						dstMethod.invoke(dst,new Object[]{srcMethod.invoke(src,new Object[]{}) });

					} else if("double".equals(dstClass.toString()) && srcClass.isAssignableFrom(BigDecimal.class)) {
						BigDecimal bigDecimal;double doubleVal=0;
						if((bigDecimal=(BigDecimal)srcMethod.invoke(src,new Object[]{}))!=null) 
							doubleVal=bigDecimal.doubleValue();
						dstMethod.invoke(dst,new Object[]{doubleVal});
					} else if("double".equals(srcClass.toString()) && dstClass.isAssignableFrom(BigDecimal.class)) {
						dstMethod.invoke(dst,new Object[]{(new BigDecimal((Double)srcMethod.invoke(src,new Object[]{})))});

					} else if(dstClass.isAssignableFrom(Double.class) && srcClass.isAssignableFrom(BigDecimal.class)) {
						BigDecimal bigDecimal;Double doubleObj=null;
						if((bigDecimal=(BigDecimal)srcMethod.invoke(src,new Object[]{}))!=null)
							doubleObj=new Double(bigDecimal.doubleValue());
						dstMethod.invoke(dst,new Object[]{doubleObj});
					} else if(srcClass.isAssignableFrom(Double.class) && dstClass.isAssignableFrom(BigDecimal.class)) {
						Double doubleObj;BigDecimal bigDecimal=null;
						if((doubleObj=(Double)srcMethod.invoke(src,new Object[]{}))!=null)
							bigDecimal=new BigDecimal(doubleObj.doubleValue());
						dstMethod.invoke(dst,new Object[]{bigDecimal});

					} else if("short".equals(dstClass.toString()) && srcClass.isAssignableFrom(Short.class)) {
						dstMethod.invoke(dst,new Object[]{((Short)srcMethod.invoke(src,new Object[]{})).shortValue()});
					} else if("short".equals(srcClass.toString()) && dstClass.isAssignableFrom(Short.class)) {
						dstMethod.invoke(dst,new Object[]{((Short)srcMethod.invoke(src,new Object[]{})).shortValue()});

					} else if(dstClass.isAssignableFrom(Character.class) && srcClass.isAssignableFrom(String.class)) {
						dstMethod.invoke(dst,new Object[]{((String)srcMethod.invoke(src,new Object[]{})).charAt(0)});
					} else if(srcClass.isAssignableFrom(Character.class) && dstClass.isAssignableFrom(String.class)) {
						dstMethod.invoke(dst,new Object[]{((Character)srcMethod.invoke(src,new Object[]{})).toString()});

					} else if("boolean".equals(dstClass.toString()) && srcClass.isAssignableFrom(Boolean.class)) {
						dstMethod.invoke(dst,new Object[]{((Boolean)srcMethod.invoke(src,new Object[]{})).booleanValue()});
					} else if("boolean".equals(srcClass.toString()) && dstClass.isAssignableFrom(Boolean.class)) {
						dstMethod.invoke(dst,new Object[]{((Boolean)srcMethod.invoke(src,new Object[]{})).booleanValue()});

						// Enumerations (web services) - src/dest classes don't match
					} else if(dstClass.isEnum() || srcClass.isEnum()) {
						Object srcVal = srcMethod.invoke(src, new Object[]{});
						if (srcVal == null) continue; // the enum was not initialized (null)
						String valueMethodName = (dstClass.isEnum())?"valueOf":"fromValue";
						Method valueMethod = dstClass.getMethod(valueMethodName, new Class[]{ String.class });
						dstMethod.invoke(dst, new Object[]{(valueMethod.invoke(dstClass, new Object[]{srcVal.toString()}))});

					} else {
						// Recursively copy classes (web services) - src/dest classes don't match
						if (recursive) {
							Object srcObj = srcMethod.invoke(src, new Object[]{});
							Object dstObj = null;
							if (srcObj != null) {
								dstObj = dstClass.getConstructor(new Class[]{}).newInstance(new Object[]{});
								copyBean(srcObj, dstObj, true);
							}
							if (!dstClass.isPrimitive()) {
								dstMethod.invoke(dst, new Object[]{dstObj});
							}
						} else {
							// must be dealt with manually
						}
					}
				} catch (NoSuchMethodException ignored) {}
			}
		} catch (ReflectionUtilClassNotSupportedException ignored) {
		} catch (Exception e) {
			throw new ReflectionUtilException(e);
		}
		return result;
	}

	/**
	 * Uses the showBeans method - see that method for details.
	 * 
	 * @param obj1 required, not null
	 * @return
	 * 
	 * @throws ReflectionUtilException
	 */
	public static String showBean(Object obj1) throws ReflectionUtilException {
		return showBeans(obj1, null);
	}

	/**
	 * Look at the getter methods of both objects and compare the values. If only one
	 * bean is passed only the values for that bean are displayed.
	 * 
	 * @param obj1 required, not null
	 * @param obj2 optional, can be null
	 * @return
	 * 
	 * @throws ReflectionUtilException
	 */
	public static String showBeans(Object obj1, Object obj2) throws ReflectionUtilException {
		StringBuilder sb = new StringBuilder();
		
		if (obj1 == null) {
			throw new ReflectionUtilException("obj1 can't be null.");
		}
		
		try {
			String eol = System.getProperty("line.separator");
			int maxMethodLength = 0;
			int maxDataLength = 30;
			
			Set<String> methods = new TreeSet<String>();
			
			// Get bean source methods for each class
			for (Method method : obj1.getClass().getMethods()) {
				if (isBeanSourceMethod(method)) {
					methods.add(method.getName());
					if (method.getName().length() > maxMethodLength) {
						maxMethodLength = method.getName().length();
					}
				}
			}
			
			if (obj2 != null) {
				for (Method method : obj2.getClass().getMethods()) {
					if (isBeanSourceMethod(method)) {
						methods.add(method.getName());
						if (method.getName().length() > maxMethodLength) {
							maxMethodLength = method.getName().length();
						}
					}
				}
			}
			
			// Loop over all methods and get the values for each object
			String obj1Val = null;
			String obj2Val = null;
			for (String method : methods) {
				if (sb.length() > 0) {
					sb.append(eol);
				}
				
				sb.append(String.format("%1$-" + maxMethodLength + "s", method));
				
				obj1Val = getBeanGetterResultAsString(obj1, method);
				if (obj2 != null) {
					obj2Val = getBeanGetterResultAsString(obj2, method);
				}
				
				if ( obj2 != null &&
						( (obj1Val != null && obj1Val.length() > maxDataLength)
								|| (obj2Val != null && obj2Val.length() > maxDataLength) ) ) {
					
					// If there are two objects put data larger than maxDataLength on the next line
					sb.append(eol);
					sb.append(String.format("%" + maxMethodLength + "s", "obj1"));
					sb.append(" ");
					sb.append(obj1Val);
					
					sb.append(eol);
					sb.append(String.format("%" + maxMethodLength + "s", "obj2"));
					sb.append(" ");
					sb.append(obj2Val);
				} else {
					// Show data compare on the same line
					sb.append(" ");
					sb.append(obj1Val);
					if (obj2 != null) {
						sb.append(" <=> ");
						sb.append(obj2Val);
					}
				}
			}
		} catch (Exception e) {
			throw new ReflectionUtilException(e);
		}
		
		return sb.toString();
	}

	/**
	 * Get the result of a bean getter method. A getter method provides access to a
	 * private field in the bean. The methodName passed to this method is expected
	 * to have no arguments.
	 * @param obj
	 * @param methodName
	 * @return
	 */
	private static String getBeanGetterResultAsString(Object obj, String methodName) {
		String result = null;
		
		try {
			Method method = obj.getClass().getMethod(methodName, new Class[]{});
			result = String.valueOf(method.invoke(obj, new Object[]{}));
		} catch (Exception e) {
			//} catch (NoSuchMethodException e) {
			//} catch (IllegalArgumentException e) {
			//} catch (IllegalAccessException e) {
			//} catch (InvocationTargetException e) {
			
			result = e.getClass().getSimpleName();
		}
		
		return result;
	}

	/**
	 * A source method for a Java bean is a getter method that allows access to
	 * private fields in the bean. Getter methods typically start with "get" or "is".
	 * If the method matches the criteria for a bean source method return true;
	 * otherwise false.
	 * 
	 * @param method - the method to evaluate
	 * @return boolean - true if the method looks like a bean source method.
	 */
	private static boolean isBeanSourceMethod(Method method) {
		boolean isSourceMethod = false;
		
		if (method != null) {
			if (
				(method.getName().length() >= 3)
				&& (method.getParameterTypes().length == 0)
				&& (!method.getName().equals("getClass"))
				&& (method.getName().startsWith("get") || method.getName().startsWith("is"))
			) {
				isSourceMethod = true;
			}
		}
		
		return isSourceMethod;
	}
	
	@SuppressWarnings("rawtypes")
	private static Class getClassForArgType(ClassArgType classArgType, Type type) throws ReflectionUtilClassNotSupportedException {
		// For collections:
		//   RawType = List, Collection, ...
		//   ActualTypeArguments = the collection parameters (object in the collection)
		if (type instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) type;
			if (classArgType == ClassArgType.RAW) {
				return (Class)pt.getRawType();
			} else if (classArgType == ClassArgType.ACTUAL_ARG) {
				return (Class)pt.getActualTypeArguments()[0];
			}
		}
		throw new ReflectionUtilClassNotSupportedException("Unexpected destination method type.");
	}

	private static Date toDate(Object object) {
		Date result = null;
		if(object==null) {
			// leave result==null
		} else if(object instanceof Date) {
			result = (Date)object;
		} else if(object instanceof XMLGregorianCalendar) {
			result = ((XMLGregorianCalendar)object).toGregorianCalendar().getTime();
		} else if(object instanceof Calendar) {
			result = ((Calendar)object).getTime();
		}
		return result;
	}

	private static Calendar toCalendar(Object object) throws ReflectionUtilDateException {
		Calendar result = null;
		try {
			if(object!=null) {
				if(object instanceof Calendar) {
					result = (Calendar)object;
				} else {
					result = Calendar.getInstance();
					if(object instanceof XMLGregorianCalendar) {
						result=((XMLGregorianCalendar)object).toGregorianCalendar();
					} else if(object instanceof Date) {
						result.setTime((Date)object);
					}
				}
			}
		} catch (Exception e) {
			throw new ReflectionUtilDateException(e);
		}
		return result;
	}

	private static XMLGregorianCalendar toXMLGregorianCalendar(Object object) throws ReflectionUtilDateException {
		XMLGregorianCalendar result = null;
		try {
			if(object!=null) {
				if(object instanceof XMLGregorianCalendar) {
					result = (XMLGregorianCalendar)object;
				} else {
					GregorianCalendar gregorianCalendar = new GregorianCalendar();
					if(object instanceof GregorianCalendar) {
						gregorianCalendar=(GregorianCalendar)object;
					} else if(object instanceof Date) {
						gregorianCalendar.setTime((Date)object);
					} else if(object instanceof Calendar) {
						gregorianCalendar.setTime(((Calendar)object).getTime());
					}
					result = DatatypeFactory.newInstance().newXMLGregorianCalendar(gregorianCalendar);
				}
			}
		} catch (Exception e) {
			throw new ReflectionUtilDateException(e);
		}
		return result;
	}

}
