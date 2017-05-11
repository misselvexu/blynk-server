import React from 'react';
import Event from './../Event';
import {EVENT_TYPES} from 'services/Products';

class Critical extends React.Component {

  static propTypes = {
    form: React.PropTypes.string,
    initialValues: React.PropTypes.object
  };

  render() {
    return (
      <Event type={EVENT_TYPES.CRITICAL} form={this.props.form} initialValues={this.props.initialValues}/>
    );
  }

}

export default Critical;
