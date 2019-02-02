import React from 'react';
import PropTypes from 'prop-types';

class EditSection extends React.Component {
  propTypes = {
    title: PropTypes.string,
    children: PropTypes.array,
  };

  render() {
    return (
      <div className="edit-section">
        {this.props.title && (<div className="edit-section-title">
          {this.props.title}
        </div>)}
        <div className="edit-section-content">
          {this.props.children}
        </div>
      </div>
    );
  }
}

export default EditSection;
