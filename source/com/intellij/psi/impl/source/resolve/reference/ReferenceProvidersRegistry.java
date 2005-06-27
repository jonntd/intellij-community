package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.ant.impl.dom.impl.RegisterInPsi;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.NamespaceFilter;
import com.intellij.psi.filters.position.ParentElementFilter;
import com.intellij.psi.filters.position.TokenTypeFilter;
import com.intellij.psi.impl.source.jsp.jspJava.JspDirective;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.manipulators.PlainFileManipulator;
import com.intellij.psi.impl.source.resolve.reference.impl.manipulators.XmlAttributeValueManipulator;
import com.intellij.psi.impl.source.resolve.reference.impl.manipulators.XmlTokenManipulator;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import com.intellij.lang.properties.PropertiesReferenceProvider;
import com.intellij.util.ArrayUtil;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:13:45
 * To change this template use Options | File Templates.
 */
public class ReferenceProvidersRegistry implements ProjectComponent {
  private final List<Class> myTempScopes = new ArrayList<Class>();
  private final List<ProviderBinding> myBindings = new ArrayList<ProviderBinding>();
  private final List<Pair<Class, ElementManipulator>> myManipulators = new ArrayList<Pair<Class, ElementManipulator>>();
  private final Map<ReferenceProviderType,PsiReferenceProvider> myReferenceTypeToProviderMap = new HashMap<ReferenceProviderType, PsiReferenceProvider>(5);

  static public class ReferenceProviderType {
    private String myId;
    private ReferenceProviderType(String id) { myId = id; }
    public String toString() { return myId; }
  }

  public static ReferenceProviderType PROPERTY_FILE_KEY_PROVIDER = new ReferenceProviderType("Property File Key Provider");
  public static ReferenceProviderType CLASS_REFERENCE_PROVIDER = new ReferenceProviderType("Class Reference Provider");
  public static ReferenceProviderType PATH_REFERENCES_PROVIDER = new ReferenceProviderType("Path References Provider");

  public static final ReferenceProvidersRegistry getInstance(Project project) {
    return project.getComponent(ReferenceProvidersRegistry.class);
  }

  private ReferenceProvidersRegistry() {
    // Temp scopes declarations
    myTempScopes.add(PsiIdentifier.class);

    // Manipulators mapping
    registerManipulator(XmlAttributeValue.class, new XmlAttributeValueManipulator());
    registerManipulator(PsiPlainTextFile.class, new PlainFileManipulator());
    registerManipulator(XmlToken.class, new XmlTokenManipulator());
    // Binding declarations

    myReferenceTypeToProviderMap.put(CLASS_REFERENCE_PROVIDER, new JavaClassReferenceProvider());
    myReferenceTypeToProviderMap.put(PATH_REFERENCES_PROVIDER, new JspxIncludePathReferenceProvider());
    myReferenceTypeToProviderMap.put(PROPERTY_FILE_KEY_PROVIDER, new PropertiesReferenceProvider());

    registerXmlAttributeValueReferenceProvider(
      new String[]{"class", "type"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new TextFilter("useBean"),
            new NamespaceFilter(XmlUtil.JSP_URI)
          ), 2
        )
      ), getProviderByType(CLASS_REFERENCE_PROVIDER)
    );

    RegisterInPsi.referenceProviders(this);

    registerXmlAttributeValueReferenceProvider(
      new String[]{"extends"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new OrFilter(
              new AndFilter(
                new ClassFilter(XmlTag.class),
                new TextFilter("directive.page")
              ),
              new AndFilter(
                new ClassFilter(JspDirective.class),
                new TextFilter("page")
              )
            ),
            new NamespaceFilter(XmlUtil.JSP_URI)
          ),
          2
        )
      ), getProviderByType(CLASS_REFERENCE_PROVIDER)
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"type"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new OrFilter(
              new AndFilter(
                new ClassFilter(XmlTag.class),
                new TextFilter("directive.attribute")
              ),
              new AndFilter(
                new ClassFilter(JspDirective.class),
                new TextFilter("attribute")
              )
            ),
            new NamespaceFilter(XmlUtil.JSP_URI)
          ),
          2
        )
      ), getProviderByType(CLASS_REFERENCE_PROVIDER)
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"variable-class"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new OrFilter(
              new AndFilter(
                new ClassFilter(XmlTag.class),
                new TextFilter("directive.variable")
              ),
              new AndFilter(
                new ClassFilter(JspDirective.class),
                new TextFilter("variable")
              )
            ),
            new NamespaceFilter(XmlUtil.JSP_URI)
          ),
          2
        )
      ), getProviderByType(CLASS_REFERENCE_PROVIDER)
    );

    registerXmlAttributeValueReferenceProvider(
      new String[] { "import" },
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new OrFilter(
              new AndFilter(
                new ClassFilter(XmlTag.class),
                new TextFilter("directive.page")
              ),
              new AndFilter(
                new ClassFilter(JspDirective.class),
                new TextFilter("page")
              )
            ),
            new NamespaceFilter(XmlUtil.JSP_URI)
          ),
          2
        )
      ),
      new JspImportListReferenceProvider()
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"errorPage"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.JSP_URI),
            new OrFilter(
              new AndFilter(
                new ClassFilter(JspDirective.class),
                new TextFilter("page")
              ),
              new AndFilter(
                new ClassFilter(XmlTag.class),
                new TextFilter("directive.page")
              ))
          ), 2
        )
      ), getProviderByType(PATH_REFERENCES_PROVIDER)
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"file"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.JSP_URI),
            new OrFilter(
              new AndFilter(
                new ClassFilter(JspDirective.class),
                new TextFilter("include")
              ),
              new AndFilter(
                new ClassFilter(XmlTag.class),
                new TextFilter("directive.include")
              ))
          ), 2
        )
      ), getProviderByType(PATH_REFERENCES_PROVIDER)
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"value"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.JSTL_CORE_URI),
            new AndFilter(
              new ClassFilter(XmlTag.class),
              new TextFilter("url")
            )
          ), 2
        )
      ), getProviderByType(PATH_REFERENCES_PROVIDER)
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"url"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.JSTL_CORE_URI),
            new AndFilter(
              new ClassFilter(XmlTag.class),
              new OrFilter(
                new TextFilter("import"),
                new TextFilter("redirect")
              )
            )
          ), 2
        )
      ), getProviderByType(PATH_REFERENCES_PROVIDER)
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"key"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new OrFilter(
              new NamespaceFilter(XmlUtil.JSTL_FORMAT_URI),
              new NamespaceFilter(XmlUtil.STRUTS_BEAN_URI)
            ),
            new AndFilter(
              new ClassFilter(XmlTag.class),
              new TextFilter("message")
            )
          ), 2
        )
      ), getProviderByType(PROPERTY_FILE_KEY_PROVIDER)
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"code"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.SPRING_URI),
            new AndFilter(
              new ClassFilter(XmlTag.class),
              new TextFilter("message", "theme")
            )
          ), 2
        )
      ), getProviderByType(PROPERTY_FILE_KEY_PROVIDER)
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"page"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.JSP_URI),
            new AndFilter(
              new ClassFilter(XmlTag.class),
              new TextFilter("include")
            )
          ), 2
        )
      ), getProviderByType(PATH_REFERENCES_PROVIDER)
    );

    registerXmlAttributeValueReferenceProvider(
      new String[]{"tagdir"},
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.JSP_URI),
            new AndFilter(
              new ClassFilter(JspDirective.class),
              new TextFilter("taglib")
            )
          ), 2
        )
      ), getProviderByType(PATH_REFERENCES_PROVIDER)
    );

    registerXmlAttributeValueReferenceProvider(
      new String[] { "uri" },
      new ScopeFilter(
        new ParentElementFilter(
          new AndFilter(
            new NamespaceFilter(XmlUtil.JSP_URI),
            new AndFilter(
              new ClassFilter(JspDirective.class),
              new TextFilter("taglib")
            )
          ), 2
        )
      ),
      new JspUriReferenceProvider()
    );

    final JavaClassListReferenceProvider classListProvider = new JavaClassListReferenceProvider();
    registerXmlAttributeValueReferenceProvider(
      null,
      new NotFilter(new ParentElementFilter(new NamespaceFilter(XmlUtil.ANT_URI), 2)),
      classListProvider
    );

    registerReferenceProvider(new TokenTypeFilter(XmlTokenType.XML_DATA_CHARACTERS), XmlToken.class,
                              classListProvider);

    //registerReferenceProvider(PsiPlainTextFile.class, new JavaClassListReferenceProvider());

    HtmlUtil.HtmlReferenceProvider provider = new HtmlUtil.HtmlReferenceProvider();
    registerXmlAttributeValueReferenceProvider(
      null,
      provider.getFilter(),
      provider
    );

    final PsiReferenceProvider filePathReferenceProvider = new FilePathReferenceProvider();
    registerReferenceProvider(PsiLiteralExpression.class, filePathReferenceProvider);
  }

  public void registerReferenceProvider(ElementFilter elementFilter, Class scope, PsiReferenceProvider provider) {
    if (scope == XmlAttributeValue.class) {
      registerXmlAttributeValueReferenceProvider(null, elementFilter, provider);
      return;
    }

    final SimpleProviderBinding binding = new SimpleProviderBinding(elementFilter, scope);
    binding.registerProvider(provider);
    myBindings.add(binding);
  }

  public void registerXmlAttributeValueReferenceProvider(String[] attributeNames, ElementFilter elementFilter, PsiReferenceProvider provider) {
    XmlAttributeValueProviderBinding attributeValueProviderBinding = null;
    for(ProviderBinding binding:myBindings) {
      if (binding instanceof XmlAttributeValueProviderBinding) {
        attributeValueProviderBinding = (XmlAttributeValueProviderBinding)binding;
        break;
      }
    }

    if (attributeValueProviderBinding == null) {
      attributeValueProviderBinding = new XmlAttributeValueProviderBinding();
      myBindings.add(attributeValueProviderBinding);
    }

    attributeValueProviderBinding.registerProvider(
      attributeNames,
      elementFilter,
      provider
    );
  }

  public PsiReferenceProvider getProviderByType(ReferenceProviderType type) {
    return myReferenceTypeToProviderMap.get(type);
  }

  public void registerReferenceProvider(Class scope, PsiReferenceProvider provider) {
    registerReferenceProvider(null, scope, provider);
  }

  public PsiReferenceProvider[] getProvidersByElement(PsiElement element) {
    PsiReferenceProvider[] ret = PsiReferenceProvider.EMPTY_ARRAY;
    PsiElement current;
    do {
      current = element;

      for (final ProviderBinding binding : myBindings) {
        final PsiReferenceProvider[] acceptableProviders = binding.getAcceptableProviders(current);
        if (acceptableProviders != null && acceptableProviders.length > 0) {
          ret = ArrayUtil.mergeArrays(ret, acceptableProviders, PsiReferenceProvider.class);
        }
      }
      element = ResolveUtil.getContext(element);
    }
    while (!isScopeFinal(current.getClass()));

    return ret;
  }

  public <T extends PsiElement> ElementManipulator<T> getManipulator(T element) {
    if(element == null) return null;

    for (final Pair<Class, ElementManipulator> pair : myManipulators) {
      if (pair.getFirst().isAssignableFrom(element.getClass())) {
        return (ElementManipulator<T>)pair.getSecond();
      }
    }

    return null;
  }

  public <T extends PsiElement> void registerManipulator(Class<T> elementClass, ElementManipulator<T> manipulator) {
    myManipulators.add(new Pair<Class, ElementManipulator>(elementClass, manipulator));
  }

  private boolean isScopeFinal(Class scopeClass) {

    for (final Class aClass : myTempScopes) {
      if (aClass.isAssignableFrom(scopeClass)) {
        return false;
      }
    }
    return true;
  }

  public void projectOpened() {}

  public void projectClosed() {}

  public String getComponentName() {
    return "Reference providers registry";
  }

  public void initComponent() {}

  public void disposeComponent() {}
}
