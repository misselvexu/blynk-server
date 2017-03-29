import styles from '../less/login.less';
import React from 'react';
import ReactDOM from 'react-dom';
import {Row, Col} from 'antd';
import {Form, Icon, Input, Button, Checkbox} from 'antd';
import enUS from 'antd/lib/locale-provider/en_US';
const FormItem = Form.Item;

function hasErrors(fieldsError) {
    return Object.keys(fieldsError).some(field => fieldsError[field]);
}

class NormalLoginForm extends React.Component {
    componentDidMount() {
        this.props.form.validateFields();
    }

    handleSubmit(e) {
        e.preventDefault();
        this.props.form.validateFields((err, values) => {
            if (!err) {

            }
        });
    }

    render() {
        const { getFieldDecorator, getFieldsError, getFieldError, isFieldTouched } = this.props.form;

        const userNameError = isFieldTouched('userName') && getFieldError('userName');
        const passwordError = isFieldTouched('password') && getFieldError('password');

        return (
            <Form onSubmit={this.handleSubmit.bind(this)} className="login-form">
                <FormItem>
                    <span className="form-header">Log in to Blynk</span>
                </FormItem>
                <FormItem validateStatus={userNameError ? 'error' : ''}
                          help={userNameError || ''}>
                  {getFieldDecorator('email', {
                    rules: [{
                      type: 'email', message: 'The input is not valid E-mail!',
                    }, {
                      required: true, message: 'Please input your E-mail!',
                    }],
                  })(
                        <Input prefix={<Icon type="user" style={{fontSize: 13}}/>} placeholder="email"/>
                    )}
                </FormItem>
                <FormItem validateStatus={passwordError ? 'error' : ''}
                          help={passwordError || ''}>
                    {getFieldDecorator('password', {
                        rules: [{required: true, message: 'Please input your Password!'}],
                    })(
                        <Input prefix={<Icon type="lock" style={{fontSize: 13}}/>} type="password"
                               placeholder="password"/>
                    )}
                </FormItem>
                <FormItem>
                    <Button type="primary" htmlType="submit" className="login-form-button"
                            disabled={hasErrors(getFieldsError())}>
                        Log in
                    </Button>

                </FormItem>
              <FormItem>
                <a className="login-form-forgot">Forgot password?</a>
              </FormItem>
            </Form>
        );
    }
}

const WrappedNormalLoginForm = Form.create()(NormalLoginForm);

ReactDOM.render(<Row type="flex" justify="space-around" align="middle" style={{height: '100%'}}>
    <Col><WrappedNormalLoginForm /></Col>
</Row>, document.getElementById('app'));
