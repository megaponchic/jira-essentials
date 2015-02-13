package ut.de.affinitas.jira-essentials;

import org.junit.Test;
import de.affinitas.jira-essentials.MyPluginComponent;
import de.affinitas.jira-essentials.MyPluginComponentImpl;

import static org.junit.Assert.assertEquals;

public class MyComponentUnitTest
{
    @Test
    public void testMyName()
    {
        MyPluginComponent component = new MyPluginComponentImpl(null);
        assertEquals("names do not match!", "myComponent",component.getName());
    }
}