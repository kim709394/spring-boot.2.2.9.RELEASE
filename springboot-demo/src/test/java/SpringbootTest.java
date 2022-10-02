import com.kim.spi.starter.bean.ImportBean;
import com.kim.spi.starter.bean.SpiBean;
import com.kim.springboot.demo.SpringbootDemoApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author huangjie
 * @description
 * @date 2022-10-03
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SpringbootDemoApplication.class)
public class SpringbootTest implements ApplicationContextAware {

	private ApplicationContext applicationContext;

	@Test
	public void spiTest(){
		SpiBean bean = applicationContext.getBean(SpiBean.class);
		System.out.println(bean);
	}

	@Test
	public void importTest(){
		ImportBean bean = applicationContext.getBean(ImportBean.class);
		System.out.println(bean);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
